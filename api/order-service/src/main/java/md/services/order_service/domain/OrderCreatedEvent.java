package md.services.order_service.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedEvent(
		String eventId,
		String orderId,
		String userId,
		String customerEmail,
		BigDecimal totalAmount,
		String currency,
		Instant occurredAt) {
}
