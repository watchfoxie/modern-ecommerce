package md.services.auth_service.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "roles")
public record RoleDocument(
		@Id String id,
		String name,
		String description,
		Instant createdAt) {
}
