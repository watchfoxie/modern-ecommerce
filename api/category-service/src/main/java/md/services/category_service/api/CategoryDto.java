package md.services.category_service.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public category representation.")
public record CategoryDto(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,
		String parentId,
		String description,
		String imageUrl,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer displayOrder,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean isActive,
		Instant createdAt,
		Instant updatedAt) {
}
