package md.services.category_service.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "categories")
public record CategoryDocument(
		@Id String id,
		String slug,
		String name,
		String parentId,
		String description,
		String imageUrl,
		Integer displayOrder,
		Boolean isActive,
		Instant createdAt,
		Instant updatedAt) {
}
