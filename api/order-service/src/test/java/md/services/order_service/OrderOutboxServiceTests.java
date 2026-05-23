package md.services.order_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import md.services.order_service.domain.OrderCreatedEvent;
import md.services.order_service.domain.OrderOutboxEventDocument;
import md.services.order_service.repository.OrderOutboxEventRepository;
import md.services.order_service.service.OrderEventCommand;
import md.services.order_service.service.OrderEventPublisher;
import md.services.order_service.service.OrderOutboxService;

@ExtendWith(MockitoExtension.class)
class OrderOutboxServiceTests {

	@Mock
	private OrderOutboxEventRepository outboxEventRepository;

	@Mock
	private OrderEventPublisher orderEventPublisher;

	@Captor
	private ArgumentCaptor<OrderOutboxEventDocument> outboxCaptor;

	@Test
	void enqueueAndDispatchPersistsPendingThenMarksDispatched() {
		when(outboxEventRepository.existsByEventTypeAndAggregateId("order.created", "order-1")).thenReturn(false);
		when(outboxEventRepository.save(any(OrderOutboxEventDocument.class))).thenAnswer(invocation -> {
			OrderOutboxEventDocument document = invocation.getArgument(0);
			return new OrderOutboxEventDocument(
					document.id() == null ? "outbox-1" : document.id(),
					document.eventType(),
					document.aggregateId(),
					document.payload(),
					document.status(),
					document.attempts(),
					document.lastError(),
					document.createdAt(),
					document.updatedAt());
		});
		when(outboxEventRepository.findTop25ByStatusOrderByCreatedAtAsc("PENDING"))
				.thenReturn(List.of(outboxDocument("outbox-1", "PENDING", 0)));

		service().enqueueAndDispatchOrderCreated(command());

		verify(orderEventPublisher).publish(any(OrderCreatedEvent.class));
		verify(outboxEventRepository, org.mockito.Mockito.atLeastOnce()).save(outboxCaptor.capture());
		assertThat(outboxCaptor.getAllValues()).extracting(OrderOutboxEventDocument::status)
				.contains("PENDING", "DISPATCHED");
	}

	@Test
	void failedDispatchLeavesEventPendingForRetry() {
		OrderOutboxEventDocument pending = outboxDocument("outbox-1", "PENDING", 0);
		when(outboxEventRepository.findTop25ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(pending));
		doThrow(new IllegalStateException("rabbit down")).when(orderEventPublisher).publish(pending.payload());

		service().dispatchPendingOutboxEvents();

		verify(outboxEventRepository).save(outboxCaptor.capture());
		assertThat(outboxCaptor.getValue().status()).isEqualTo("PENDING");
		assertThat(outboxCaptor.getValue().attempts()).isEqualTo(1);
		assertThat(outboxCaptor.getValue().lastError()).isEqualTo("rabbit down");
	}

	private OrderOutboxService service() {
		return new OrderOutboxService(outboxEventRepository, orderEventPublisher);
	}

	private OrderEventCommand command() {
		return new OrderEventCommand("order-1", "user-1", "customer@example.com", new BigDecimal("100.00"), "MDL");
	}

	private OrderOutboxEventDocument outboxDocument(String id, String status, int attempts) {
		Instant now = Instant.parse("2026-05-01T12:00:00Z");
		return new OrderOutboxEventDocument(
				id,
				"order.created",
				"order-1",
				new OrderCreatedEvent("event-1", "order-1", "user-1", "customer@example.com",
						new BigDecimal("100.00"), "MDL", now),
				status,
				attempts,
				null,
				now,
				now);
	}
}
