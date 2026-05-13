package md.services.product_service.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public product representation.")
public record ProductDto(
		String id,
		String categoryId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String categorySlug,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String brand,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String model,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String country,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal price,
		BigDecimal promotionalPrice,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer stock,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> imageUrls,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, String> specs,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean isActive,
		Instant createdAt,
		Instant updatedAt) {
}
