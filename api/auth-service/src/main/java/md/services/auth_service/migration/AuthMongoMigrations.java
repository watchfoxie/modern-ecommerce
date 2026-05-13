package md.services.auth_service.migration;

import java.time.Instant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Configuration(proxyBeanMethods = false)
public class AuthMongoMigrations {

	@Bean
	MongoMigration auth001CollectionsAndIndexes() {
		return new MongoMigration() {
			@Override
			public String version() {
				return "001";
			}

			@Override
			public String description() {
				return "Create auth users and roles collections with canonical indexes.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				ensureCollection(mongoTemplate, "users");
				ensureCollection(mongoTemplate, "roles");
				mongoTemplate.indexOps("users")
						.ensureIndex(new Index().on("email", Sort.Direction.ASC).unique().named("email"));
				mongoTemplate.indexOps("users")
						.ensureIndex(new Index().on("passwordResetToken", Sort.Direction.ASC).unique().sparse()
								.named("passwordResetToken"));
				mongoTemplate.indexOps("roles")
						.ensureIndex(new Index().on("name", Sort.Direction.ASC).unique().named("name"));
			}
		};
	}

	@Bean
	MongoMigration auth002SeedRoles() {
		return new MongoMigration() {
			@Override
			public String version() {
				return "002";
			}

			@Override
			public String description() {
				return "Seed canonical Spring Security roles.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				ensureRole(mongoTemplate, "ROLE_USER", "Authenticated customer role.");
				ensureRole(mongoTemplate, "ROLE_ADMIN", "Administrator role for catalog and order management.");
			}
		};
	}

	private static void ensureCollection(MongoTemplate mongoTemplate, String collectionName) {
		if (!mongoTemplate.collectionExists(collectionName)) {
			mongoTemplate.createCollection(collectionName);
		}
	}

	private static void ensureRole(MongoTemplate mongoTemplate, String name, String description) {
		mongoTemplate.upsert(
				Query.query(Criteria.where("name").is(name)),
				new Update()
						.set("description", description)
						.setOnInsert("name", name)
						.setOnInsert("createdAt", Instant.now()),
				"roles");
	}
}
