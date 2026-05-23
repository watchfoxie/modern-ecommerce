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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request used by administrators to create or update a product.")
public record ProductUpsertRequest(
		@NotBlank String categoryId,
		@NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be a lowercase URL slug") String categorySlug,
		@NotBlank @Size(max = 160) String name,
		@NotBlank @Size(max = 180)
		@Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be a lowercase URL slug") String slug,
		@NotBlank @Size(max = 80) String brand,
		@NotBlank @Size(max = 120) String model,
		@NotBlank @Size(max = 80) String country,
		@NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
		@DecimalMin(value = "0.0", inclusive = false) BigDecimal promotionalPrice,
		@NotBlank @Size(min = 3, max = 3) String currency,
		@NotNull @Min(0) Integer stock,
		@NotEmpty List<@NotBlank String> imageUrls,
		@NotNull Map<@NotBlank String, @NotBlank String> specs,
		@NotNull Boolean isActive) {
}
