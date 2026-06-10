package md.services.order_service.client;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service", url = "${app.clients.product-service-url:}")
public interface ProductInternalClient {

	@GetMapping("/internal/products/{productId}")
	ProductInternalDto getProduct(@PathVariable String productId);

	@PostMapping(value = "/internal/products/{productId}/stock/decrement", consumes = MediaType.APPLICATION_JSON_VALUE)
	void decrementStock(@PathVariable String productId, @RequestBody StockDecrementRequest request);

	record StockDecrementRequest(Integer quantity) {
	}

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
