package md.services.notification_service.domain;

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
