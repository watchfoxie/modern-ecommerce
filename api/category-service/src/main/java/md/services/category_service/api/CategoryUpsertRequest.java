package md.services.category_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request used by administrators to create or update a category.")
public record CategoryUpsertRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 2, max = 120) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 2, max = 140) @Pattern(regexp = "^[a-z0-9]++(?:-[a-z0-9]++)*+$", message = "must be a lowercase URL slug") String slug,
		String parentId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 500) String description,
		@Size(max = 512) String imageUrl,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) Integer displayOrder,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean isActive) {
}
