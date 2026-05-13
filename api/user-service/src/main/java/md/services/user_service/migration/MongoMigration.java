package md.services.user_service.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.data.mongodb.core.MongoTemplate;

public interface MongoMigration {

	String version();

	String description();

	void migrate(MongoTemplate mongoTemplate);

	default void rollback(MongoTemplate mongoTemplate) {
		throw new UnsupportedOperationException("Rollback is reserved for controlled operational procedures.");
	}

	default String checksum() {
		return sha256(getClass().getName() + "|" + version() + "|" + description());
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 digest algorithm is unavailable.", exception);
		}
	}
}
