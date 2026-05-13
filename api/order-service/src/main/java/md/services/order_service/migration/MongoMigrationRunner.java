package md.services.order_service.migration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnProperty(value = "app.data.migrations.enabled", havingValue = "true", matchIfMissing = true)
public class MongoMigrationRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(MongoMigrationRunner.class);
	private static final String MIGRATION_COLLECTION = "_schema_migrations";

	private final MongoTemplate mongoTemplate;
	private final List<MongoMigration> migrations;
	private final boolean failFast;

	public MongoMigrationRunner(
			MongoTemplate mongoTemplate,
			List<MongoMigration> migrations,
			@Value("${app.data.migrations.fail-fast:true}") boolean failFast) {
		this.mongoTemplate = mongoTemplate;
		this.migrations = List.copyOf(migrations);
		this.failFast = failFast;
	}

	@Override
	public void run(ApplicationArguments args) {
		ensureMigrationCollection();
		for (MongoMigration migration : orderedMigrations()) {
			apply(migration);
		}
	}

	private List<MongoMigration> orderedMigrations() {
		Map<String, MongoMigration> ordered = new TreeMap<>();
		for (MongoMigration migration : migrations) {
			MongoMigration previous = ordered.putIfAbsent(migration.version(), migration);
			if (previous != null) {
				throw new IllegalStateException("Duplicate MongoDB migration version: " + migration.version());
			}
		}
		return List.copyOf(ordered.values());
	}

	private void ensureMigrationCollection() {
		if (!mongoTemplate.collectionExists(MIGRATION_COLLECTION)) {
			mongoTemplate.createCollection(MIGRATION_COLLECTION);
		}
		mongoTemplate.indexOps(MIGRATION_COLLECTION)
				.ensureIndex(new Index().on("version", Sort.Direction.ASC).unique()
						.named("schema_migrations_version_unique_idx"));
	}

	private void apply(MongoMigration migration) {
		String checksum = migration.checksum();
		Document applied = mongoTemplate.findOne(
				Query.query(Criteria.where("version").is(migration.version())),
				Document.class,
				MIGRATION_COLLECTION);
		if (applied != null) {
			String appliedChecksum = applied.getString("checksum");
			if (!Objects.equals(appliedChecksum, checksum)) {
				String message = "MongoDB migration checksum mismatch for version " + migration.version();
				if (failFast) {
					throw new IllegalStateException(message);
				}
				LOGGER.warn(message);
			}
			return;
		}

		try {
			migration.migrate(mongoTemplate);
			mongoTemplate.insert(new Document()
					.append("version", migration.version())
					.append("description", migration.description())
					.append("checksum", checksum)
					.append("appliedAt", Instant.now()), MIGRATION_COLLECTION);
			LOGGER.info("Applied MongoDB migration {} - {}", migration.version(), migration.description());
		}
		catch (RuntimeException exception) {
			if (failFast) {
				throw exception;
			}
			LOGGER.error("MongoDB migration {} failed and fail-fast is disabled.", migration.version(), exception);
		}
	}
}
