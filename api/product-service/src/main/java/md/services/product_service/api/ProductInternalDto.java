package md.services.product_service.api;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProductInternalDto", description = "Internal product snapshot used by cart and order services")
public record ProductInternalDto(
		String id,
		String name,
		String brand,
		String categorySlug,
		BigDecimal price,
		BigDecimal promotionalPrice,
		String currency,
		Integer stock,
		List<String> imageUrls,
		Boolean isActive) {
}
