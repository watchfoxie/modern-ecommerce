package md.services.product_service.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

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
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;

@Configuration(proxyBeanMethods = false)
public class ProductMongoMigrations {

	@Bean
	MongoMigration product001CollectionsAndIndexes() {
		return new MongoMigration() {
			@Override
			public String version() {
				return "001";
			}

			@Override
			public String description() {
				return "Create product collection with catalog, promotion, and search indexes.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				ensureCollection(mongoTemplate, "products");
				mongoTemplate.indexOps("products")
						.ensureIndex(new Index().on("slug", Sort.Direction.ASC).unique().named("slug"));
				mongoTemplate.indexOps("products")
						.ensureIndex(new Index().on("categorySlug", Sort.Direction.ASC).named("categorySlug"));
				mongoTemplate.indexOps("products")
						.ensureIndex(new Index()
								.on("categorySlug", Sort.Direction.ASC)
								.on("promotionalPrice", Sort.Direction.ASC)
								.named("category_promotion_idx"));
				mongoTemplate.indexOps("products")
						.ensureIndex(new Index()
								.on("isActive", Sort.Direction.ASC)
								.on("categorySlug", Sort.Direction.ASC)
								.on("createdAt", Sort.Direction.DESC)
								.named("products_active_category_created_idx"));
				mongoTemplate.indexOps("products")
						.ensureIndex(new Index()
								.on("isActive", Sort.Direction.ASC)
								.on("promotionalPrice", Sort.Direction.ASC)
								.named("products_active_promotion_idx"));
				mongoTemplate.indexOps("products")
						.ensureIndex(new Index()
								.on("isActive", Sort.Direction.ASC)
								.on("price", Sort.Direction.ASC)
								.named("products_active_price_idx"));
				mongoTemplate.indexOps("products")
						.ensureIndex(new Index().on("updatedAt", Sort.Direction.DESC).named("products_updated_idx"));
				mongoTemplate.getCollection("products")
						.createIndex(Indexes.compoundIndex(
										Indexes.text("name"),
										Indexes.text("brand"),
										Indexes.text("model")),
								new IndexOptions().name("products_text_search_idx"));
			}
		};
	}

	@Bean
	MongoMigration product002NormalizeMoneyFields() {
		return new MongoMigration() {
			@Override
			public String version() {
				return "002";
			}

			@Override
			public String description() {
				return "Normalize product monetary fields to Decimal128-compatible values.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				mongoTemplate.getCollection("products").updateMany(new Document(), List.of(
						new Document("$set", new Document()
								.append("price", new Document("$toDecimal", "$price"))
								.append("promotionalPrice", new Document("$cond", Arrays.asList(
										new Document("$ne", Arrays.asList("$promotionalPrice", null)),
										new Document("$toDecimal", "$promotionalPrice"),
										null))))));
			}
		};
	}

	@Bean
	@ConditionalOnProperty(value = "app.data.seed.enabled", havingValue = "true")
	MongoMigration product003SeedSampleData(
			ObjectMapper objectMapper,
			@Value("${app.data.seed.products-path:../../static/sample-data/structured-data/product-service.products.json}")
			String seedPath) {
		return new MongoMigration() {
			@Override
			public String version() {
				return "003";
			}

			@Override
			public String description() {
				return "Seed product sample data with slug-based upserts.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				seedProducts(mongoTemplate, objectMapper, Path.of(seedPath));
			}
		};
	}

	private static void ensureCollection(MongoTemplate mongoTemplate, String collectionName) {
		if (!mongoTemplate.collectionExists(collectionName)) {
			mongoTemplate.createCollection(collectionName);
		}
	}

	private static void seedProducts(MongoTemplate mongoTemplate, ObjectMapper objectMapper, Path seedPath) {
		Path normalized = seedPath.normalize();
		if (!Files.isRegularFile(normalized)) {
			throw new IllegalStateException("Product seed file was not found: " + normalized);
		}
		try {
			JsonNode root = objectMapper.readTree(normalized.toFile());
			if (!root.isArray()) {
				throw new IllegalStateException("Product seed file must contain a JSON array: " + normalized);
			}
			for (JsonNode node : root) {
				Document document = Document.parse(objectMapper.writeValueAsString(node));
				Object slug = document.get("slug");
				if (!(slug instanceof String slugValue) || slugValue.isBlank()) {
					throw new IllegalStateException("Product seed document is missing a non-empty slug.");
				}
				mongoTemplate.getCollection("products")
						.replaceOne(Filters.eq("slug", slugValue), document, new ReplaceOptions().upsert(true));
			}
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not read product seed file: " + normalized, exception);
		}
	}
}
