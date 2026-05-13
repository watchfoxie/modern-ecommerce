package md.services.product_service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "products")
public record ProductDocument(
		@Id String id,
		String categoryId,
		String categorySlug,
		String name,
		String slug,
		String brand,
		String model,
		String country,
		BigDecimal price,
		BigDecimal promotionalPrice,
		String currency,
		Integer stock,
		List<String> imageUrls,
		Map<String, String> specs,
		Boolean isActive,
		Instant createdAt,
		Instant updatedAt) {
}
