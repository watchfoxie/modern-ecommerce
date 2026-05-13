package md.services.cart_service.api;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authenticated user's persistent cart.")
public record CartDto(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String userId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CartItemDto> items,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt) {
}
