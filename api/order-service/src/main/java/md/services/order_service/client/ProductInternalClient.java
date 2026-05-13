package md.services.order_service.client;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", url = "${app.clients.product-service-url:}")
public interface ProductInternalClient {

	@GetMapping("/internal/products/{productId}")
	ProductInternalDto getProduct(@PathVariable String productId);

	record ProductInternalDto(
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

		public BigDecimal effectivePrice() {
			return promotionalPrice != null ? promotionalPrice : price;
		}

		public String primaryImageUrl() {
			return imageUrls == null || imageUrls.isEmpty() ? "" : imageUrls.getFirst();
		}
	}
}
