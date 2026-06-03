package md.services.order_service.client;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "cart-service", url = "${app.clients.cart-service-url:}")
public interface CartInternalClient {

	@GetMapping("/carts/me")
	CartDto getCurrentCart(@RequestHeader("X-User-Id") String userId);

	@DeleteMapping("/v1/carts/me")
	void clearCurrentCart(@RequestHeader("X-User-Id") String userId);

	record CartDto(
			String id,
			String userId,
			List<CartItemDto> items,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
	}

	record CartItemDto(
			String productId,
			int quantity,
			BigDecimal priceAtAdd,
			ProductSnapshotDto productSnapshot) {
	}

	record ProductSnapshotDto(
			String name,
			String imageUrl,
			String categorySlug) {
	}
}
