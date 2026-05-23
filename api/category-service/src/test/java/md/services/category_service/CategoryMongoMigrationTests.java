package md.services.category_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import md.services.category_service.migration.CategoryMongoMigrations;
import md.services.category_service.migration.MongoMigrationRunner;

@DataMongoTest(properties = "app.data.migrations.enabled=true")
@Import({ MongoMigrationRunner.class, CategoryMongoMigrations.class })
@Testcontainers
class CategoryMongoMigrationTests {

	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		registry.add("CATEGORY_MONGODB_URI", MONGO::getReplicaSetUrl);
		registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
		registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
	}

	@Autowired
	private MongoTemplate mongoTemplate;

	@Test
	void appliesCategoryIndexesAndMigrationRecord() {
		assertThat(mongoTemplate.collectionExists("categories")).isTrue();
		assertThat(indexNames("categories")).contains(
				"slug",
				"categories_active_display_name_idx",
				"categories_parent_active_display_name_idx");
		assertThat(index("categories", "slug").isUnique()).isTrue();
		assertThat(mongoTemplate.getCollection("_schema_migrations").countDocuments()).isEqualTo(1);
	}

	@Test
	void enforcesUniqueCategorySlug() {
		mongoTemplate.insert(new Document("slug", "smartphones"), "categories");

		assertThatThrownBy(() -> mongoTemplate.insert(new Document("slug", "smartphones"), "categories"))
				.isInstanceOf(DuplicateKeyException.class);
	}

	private Set<String> indexNames(String collection) {
		return mongoTemplate.indexOps(collection).getIndexInfo().stream()
				.map(IndexInfo::getName)
				.collect(Collectors.toSet());
	}

	private IndexInfo index(String collection, String name) {
		return mongoTemplate.indexOps(collection).getIndexInfo().stream()
				.filter(index -> name.equals(index.getName()))
				.findFirst()
				.orElseThrow();
	}
}
