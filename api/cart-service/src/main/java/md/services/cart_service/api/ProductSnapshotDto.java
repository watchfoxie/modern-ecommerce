package md.services.cart_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Denormalized product snapshot captured when an item is added to the cart.")
public record ProductSnapshotDto(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String imageUrl,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String categorySlug) {
}
