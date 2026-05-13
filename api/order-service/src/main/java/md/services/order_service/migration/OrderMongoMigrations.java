package md.services.order_service.migration;

import java.util.List;

import org.bson.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration(proxyBeanMethods = false)
public class OrderMongoMigrations {

	@Bean
	MongoMigration order001CollectionsAndIndexes() {
		return new MongoMigration() {
			@Override
			public String version() {
				return "001";
			}

			@Override
			public String description() {
				return "Create order collection with history and dashboard indexes.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				ensureCollection(mongoTemplate, "orders");
				mongoTemplate.indexOps("orders")
						.ensureIndex(new Index().on("orderNumber", Sort.Direction.ASC).unique().named("orderNumber"));
				mongoTemplate.indexOps("orders")
						.ensureIndex(new Index()
								.on("userId", Sort.Direction.ASC)
								.on("createdAt", Sort.Direction.DESC)
								.named("orders_user_created_idx"));
				mongoTemplate.indexOps("orders")
						.ensureIndex(new Index()
								.on("status", Sort.Direction.ASC)
								.on("createdAt", Sort.Direction.DESC)
								.named("orders_status_created_idx"));
				mongoTemplate.indexOps("orders")
						.ensureIndex(new Index()
								.on("userId", Sort.Direction.ASC)
								.on("status", Sort.Direction.ASC)
								.named("orders_user_status_idx"));
			}
		};
	}

	@Bean
	MongoMigration order002NormalizeMoneyFields() {
		return new MongoMigration() {
			@Override
			public String version() {
				return "002";
			}

			@Override
			public String description() {
				return "Normalize order monetary fields to Decimal128-compatible values.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				Document mapItems = new Document("$map", new Document()
						.append("input", "$items")
						.append("as", "item")
						.append("in", new Document("$mergeObjects", List.of(
								"$$item",
								new Document("unitPrice", new Document("$toDecimal", "$$item.unitPrice"))))));
				mongoTemplate.getCollection("orders").updateMany(new Document(), List.of(
						new Document("$set", new Document()
								.append("totalAmount", new Document("$toDecimal", "$totalAmount"))
								.append("items", mapItems))));
			}
		};
	}

	private static void ensureCollection(MongoTemplate mongoTemplate, String collectionName) {
		if (!mongoTemplate.collectionExists(collectionName)) {
			mongoTemplate.createCollection(collectionName);
		}
	}
}
