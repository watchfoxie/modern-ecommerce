package md.services.order_service.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "order_outbox")
public record OrderOutboxEventDocument(
		@Id String id,
		String eventType,
		String aggregateId,
		OrderCreatedEvent payload,
		String status,
		int attempts,
		String lastError,
		Instant createdAt,
		Instant updatedAt) {
}
