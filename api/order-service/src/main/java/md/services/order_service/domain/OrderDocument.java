package md.services.order_service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "orders")
public record OrderDocument(
		@Id String id,
		String userId,
		String customerEmail,
		String orderNumber,
		List<OrderItem> items,
		DeliveryAddress deliveryAddress,
		Payment payment,
		String status,
		BigDecimal totalAmount,
		String currency,
		String notes,
		Instant createdAt,
		Instant updatedAt) {

	public record OrderItem(
			String productId,
			String name,
			String brand,
			String imageUrl,
			int quantity,
			BigDecimal unitPrice) {
	}

	public record DeliveryAddress(
			String street,
			String city,
			String district,
			String postalCode,
			String recipientName,
			String recipientPhone) {
	}

	public record Payment(
			String method,
			String status,
			String transactionId) {
	}
}
