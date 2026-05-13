package md.services.product_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import md.services.product_service.migration.MongoMigrationRunner;
import md.services.product_service.migration.ProductMongoMigrations;

@DataMongoTest(properties = "app.data.migrations.enabled=true")
@Import({ MongoMigrationRunner.class, ProductMongoMigrations.class })
@Testcontainers
class ProductMongoMigrationTests {

	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		registry.add("PRODUCT_MONGODB_URI", MONGO::getReplicaSetUrl);
		registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
		registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
	}

	@Autowired
	private MongoTemplate mongoTemplate;

	@Test
	void appliesProductIndexesAndMigrationRecord() {
		assertThat(mongoTemplate.collectionExists("products")).isTrue();
		assertThat(indexNames("products")).contains(
				"slug",
				"categorySlug",
				"category_promotion_idx",
				"products_active_category_created_idx",
				"products_active_promotion_idx",
				"products_active_price_idx",
				"products_updated_idx",
				"products_text_search_idx");
		assertThat(index("products", "slug").isUnique()).isTrue();
		assertThat(mongoTemplate.getCollection("_schema_migrations").countDocuments()).isEqualTo(2);
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
