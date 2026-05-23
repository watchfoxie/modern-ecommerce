package md.services.order_service;

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

import md.services.order_service.migration.MongoMigrationRunner;
import md.services.order_service.migration.OrderMongoMigrations;

@DataMongoTest(properties = "app.data.migrations.enabled=true")
@Import({ MongoMigrationRunner.class, OrderMongoMigrations.class })
@Testcontainers
class OrderMongoMigrationTests {

	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		registry.add("ORDER_MONGODB_URI", MONGO::getReplicaSetUrl);
		registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
		registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
	}

	@Autowired
	private MongoTemplate mongoTemplate;

	@Test
	void appliesOrderIndexesAndMigrationRecord() {
		assertThat(mongoTemplate.collectionExists("orders")).isTrue();
		assertThat(mongoTemplate.collectionExists("order_outbox")).isTrue();
		assertThat(indexNames("orders")).contains(
				"orderNumber",
				"orders_user_created_idx",
				"orders_status_created_idx",
				"orders_user_status_idx");
		assertThat(indexNames("order_outbox")).contains(
				"order_outbox_event_aggregate_idx",
				"order_outbox_status_created_idx");
		assertThat(index("orders", "orderNumber").isUnique()).isTrue();
		assertThat(index("order_outbox", "order_outbox_event_aggregate_idx").isUnique()).isTrue();
		assertThat(mongoTemplate.getCollection("_schema_migrations").countDocuments()).isEqualTo(3);
	}

	@Test
	void enforcesUniqueOrderNumber() {
		mongoTemplate.insert(new Document("orderNumber", "ORD-UNIQUE"), "orders");

		assertThatThrownBy(() -> mongoTemplate.insert(new Document("orderNumber", "ORD-UNIQUE"), "orders"))
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
