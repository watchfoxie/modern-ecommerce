package md.services.user_service.migration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Configuration(proxyBeanMethods = false)
public class UserMongoMigrations {

	@Bean
	MongoMigration user001CollectionsAndIndexes() {
		return new MongoMigration() {
			@Override
			public String version() {
				return "001";
			}

			@Override
			public String description() {
				return "Create user profile collection with canonical identity indexes.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				ensureCollection(mongoTemplate, "users");
				mongoTemplate.indexOps("users")
						.ensureIndex(new Index().on("authId", Sort.Direction.ASC).unique().named("authId"));
				mongoTemplate.indexOps("users")
						.ensureIndex(new Index().on("email", Sort.Direction.ASC).unique().named("email"));
			}
		};
	}

	@Bean
	MongoMigration user002BackfillActiveFlag() {
		return new MongoMigration() {
			@Override
			public String version() {
				return "002";
			}

			@Override
			public String description() {
				return "Backfill active flag for existing user profile documents.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				mongoTemplate.updateMulti(
						Query.query(Criteria.where("active").exists(false)),
						new Update().set("active", true),
						"users");
			}
		};
	}

	private static void ensureCollection(MongoTemplate mongoTemplate, String collectionName) {
		if (!mongoTemplate.collectionExists(collectionName)) {
			mongoTemplate.createCollection(collectionName);
		}
	}
}
