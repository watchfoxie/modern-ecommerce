package md.services.category_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request used by administrators to create or update a category.")
public record CategoryUpsertRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 120) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 140) String slug,
		String parentId,
		@Size(max = 500) String description,
		String imageUrl,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) Integer displayOrder,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean isActive) {
}
