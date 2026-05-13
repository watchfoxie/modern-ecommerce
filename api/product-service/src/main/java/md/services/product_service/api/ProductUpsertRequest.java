package md.services.product_service.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request used by administrators to create or update a product.")
public record ProductUpsertRequest(
		@NotBlank String categoryId,
		@NotBlank String categorySlug,
		@NotBlank @Size(max = 160) String name,
		@NotBlank @Size(max = 180) String slug,
		@NotBlank String brand,
		@NotBlank String model,
		@NotBlank String country,
		@NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
		@DecimalMin(value = "0.0", inclusive = false) BigDecimal promotionalPrice,
		@NotBlank String currency,
		@NotNull @Min(0) Integer stock,
		@NotEmpty List<String> imageUrls,
		@NotNull Map<String, String> specs,
		@NotNull Boolean isActive) {
}
