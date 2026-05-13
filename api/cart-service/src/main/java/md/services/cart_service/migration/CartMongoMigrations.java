package md.services.cart_service.migration;

import java.util.List;

import org.bson.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration(proxyBeanMethods = false)
public class CartMongoMigrations {

	@Bean
	MongoMigration cart001CollectionsAndIndexes() {
		return new MongoMigration() {
			@Override
			public String version() {
				return "001";
			}

			@Override
			public String description() {
				return "Create cart collection with one-cart-per-user unique index.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				ensureCollection(mongoTemplate, "carts");
				mongoTemplate.indexOps("carts")
						.ensureIndex(new Index().on("userId", Sort.Direction.ASC).unique().named("userId"));
			}
		};
	}

	@Bean
	MongoMigration cart002NormalizeMoneyFields() {
		return new MongoMigration() {
			@Override
			public String version() {
				return "002";
			}

			@Override
			public String description() {
				return "Normalize embedded cart item priceAtAdd fields to Decimal128-compatible values.";
			}

			@Override
			public void migrate(MongoTemplate mongoTemplate) {
				Document mapItems = new Document("$map", new Document()
						.append("input", "$items")
						.append("as", "item")
						.append("in", new Document("$mergeObjects", List.of(
								"$$item",
								new Document("priceAtAdd", new Document("$toDecimal", "$$item.priceAtAdd"))))));
				mongoTemplate.getCollection("carts").updateMany(
						new Document("items.priceAtAdd", new Document("$type", "string")),
						List.of(new Document("$set", new Document("items", mapItems))));
			}
		};
	}

	private static void ensureCollection(MongoTemplate mongoTemplate, String collectionName) {
		if (!mongoTemplate.collectionExists(collectionName)) {
			mongoTemplate.createCollection(collectionName);
		}
	}
}
