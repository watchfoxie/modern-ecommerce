package md.services.category_service.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

@Configuration(proxyBeanMethods = false)
public class CategoryMongoMigrations {

	@Bean
	MongoMigration category001CollectionsAndIndexes() {
		return new MongoMigration() {
			@Override
			public String version() {
				return "001";
			}

			@Override
			public String description() {
				return "Create category collection with catalog navigation indexes.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				ensureCollection(mongoTemplate, "categories");
				mongoTemplate.indexOps("categories")
						.ensureIndex(new Index().on("slug", Sort.Direction.ASC).unique().named("slug"));
				mongoTemplate.indexOps("categories")
						.ensureIndex(new Index()
								.on("isActive", Sort.Direction.ASC)
								.on("displayOrder", Sort.Direction.ASC)
								.on("name", Sort.Direction.ASC)
								.named("categories_active_display_name_idx"));
				mongoTemplate.indexOps("categories")
						.ensureIndex(new Index()
								.on("parentId", Sort.Direction.ASC)
								.on("isActive", Sort.Direction.ASC)
								.on("displayOrder", Sort.Direction.ASC)
								.on("name", Sort.Direction.ASC)
								.named("categories_parent_active_display_name_idx"));
			}
		};
	}

	@Bean
	@ConditionalOnProperty(value = "app.data.seed.enabled", havingValue = "true")
	MongoMigration category002SeedSampleData(
			ObjectMapper objectMapper,
			@Value("${app.data.seed.categories-path:../../static/sample-data/structured-data/category-service.categories.json}")
			String seedPath) {
		return new MongoMigration() {
			@Override
			public String version() {
				return "002";
			}

			@Override
			public String description() {
				return "Seed category sample data with slug-based upserts.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				seedCategories(mongoTemplate, objectMapper, Path.of(seedPath));
			}
		};
	}

	private static void ensureCollection(MongoTemplate mongoTemplate, String collectionName) {
		if (!mongoTemplate.collectionExists(collectionName)) {
			mongoTemplate.createCollection(collectionName);
		}
	}

	private static void seedCategories(MongoTemplate mongoTemplate, ObjectMapper objectMapper, Path seedPath) {
		Path normalized = seedPath.normalize();
		if (!Files.isRegularFile(normalized)) {
			throw new IllegalStateException("Category seed file was not found: " + normalized);
		}
		try {
			JsonNode root = objectMapper.readTree(normalized.toFile());
			if (!root.isArray()) {
				throw new IllegalStateException("Category seed file must contain a JSON array: " + normalized);
			}
			for (JsonNode node : root) {
				Document document = Document.parse(objectMapper.writeValueAsString(node));
				Object slug = document.get("slug");
				if (!(slug instanceof String slugValue) || slugValue.isBlank()) {
					throw new IllegalStateException("Category seed document is missing a non-empty slug.");
				}
				mongoTemplate.getCollection("categories")
						.replaceOne(Filters.eq("slug", slugValue), document, new ReplaceOptions().upsert(true));
			}
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not read category seed file: " + normalized, exception);
		}
	}
}
