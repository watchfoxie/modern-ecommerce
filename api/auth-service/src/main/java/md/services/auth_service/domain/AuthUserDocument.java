package md.services.auth_service.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public record AuthUserDocument(
		@Id String id,
		String email,
		String passwordHash,
		List<String> roleIds,
		String status,
		String passwordResetToken,
		Instant passwordResetExpiry,
		String refreshTokenHash,
		Instant refreshTokenExpiry,
		Instant createdAt,
		Instant updatedAt,
		Instant lastLoginAt) {
}
