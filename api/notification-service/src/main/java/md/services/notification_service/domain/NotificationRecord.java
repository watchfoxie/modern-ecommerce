package md.services.notification_service.domain;

import java.time.Instant;

public record NotificationRecord(
		String eventId,
		String orderId,
		String customerEmail,
		String status,
		String details,
		Instant processedAt) {
}
