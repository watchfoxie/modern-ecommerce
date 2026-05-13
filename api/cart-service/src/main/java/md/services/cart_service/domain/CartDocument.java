package md.services.cart_service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "carts")
public record CartDocument(
		@Id String id,
		String userId,
		List<CartItem> items,
		Instant createdAt,
		Instant updatedAt) {

	public record CartItem(
			String productId,
			int quantity,
			BigDecimal priceAtAdd,
			ProductSnapshot productSnapshot) {
	}

	public record ProductSnapshot(
			String name,
			String imageUrl,
			String categorySlug) {
	}
}
