package md.services.user_service;

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

import md.services.user_service.migration.MongoMigrationRunner;
import md.services.user_service.migration.UserMongoMigrations;

@DataMongoTest(properties = "app.data.migrations.enabled=true")
@Import({ MongoMigrationRunner.class, UserMongoMigrations.class })
@Testcontainers
class UserMongoMigrationTests {

	@Container
	static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		registry.add("USER_MONGODB_URI", MONGO::getReplicaSetUrl);
		registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
		registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
	}

	@Autowired
	private MongoTemplate mongoTemplate;

	@Test
	void appliesUserIndexesAndMigrationRecord() {
		assertThat(mongoTemplate.collectionExists("users")).isTrue();
		assertThat(indexNames("users")).contains("authId", "email");
		assertThat(index("users", "authId").isUnique()).isTrue();
		assertThat(index("users", "email").isUnique()).isTrue();
		assertThat(mongoTemplate.getCollection("_schema_migrations").countDocuments()).isEqualTo(2);
	}

	@Test
	void enforcesUniqueUserAuthIdAndEmail() {
		mongoTemplate.insert(new Document("authId", "auth-unique").append("email", "unique@example.com"), "users");

		assertThatThrownBy(() -> mongoTemplate.insert(
				new Document("authId", "auth-unique").append("email", "other@example.com"), "users"))
				.isInstanceOf(DuplicateKeyException.class);
		assertThatThrownBy(() -> mongoTemplate.insert(
				new Document("authId", "auth-other").append("email", "unique@example.com"), "users"))
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
