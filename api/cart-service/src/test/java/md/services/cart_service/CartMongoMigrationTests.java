package md.services.cart_service;

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

import md.services.cart_service.migration.CartMongoMigrations;
import md.services.cart_service.migration.MongoMigrationRunner;

@DataMongoTest(properties = "app.data.migrations.enabled=true")
@Import({ MongoMigrationRunner.class, CartMongoMigrations.class })
@Testcontainers
class CartMongoMigrationTests {

	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		registry.add("CART_MONGODB_URI", MONGO::getReplicaSetUrl);
		registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
		registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
	}

	@Autowired
	private MongoTemplate mongoTemplate;

	@Test
	void appliesCartIndexesAndMigrationRecord() {
		assertThat(mongoTemplate.collectionExists("carts")).isTrue();
		assertThat(indexNames("carts")).contains("userId");
		assertThat(index("carts", "userId").isUnique()).isTrue();
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
