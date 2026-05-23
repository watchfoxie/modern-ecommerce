package md.services.order_service.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import md.services.order_service.domain.OrderCreatedEvent;
import md.services.order_service.domain.OrderOutboxEventDocument;
import md.services.order_service.repository.OrderOutboxEventRepository;

@Service
public class OrderOutboxService {

	static final String EVENT_TYPE_ORDER_CREATED = "order.created";
	static final String STATUS_PENDING = "PENDING";
	static final String STATUS_DISPATCHED = "DISPATCHED";

	private final OrderOutboxEventRepository outboxEventRepository;
	private final OrderEventPublisher orderEventPublisher;

	public OrderOutboxService(OrderOutboxEventRepository outboxEventRepository,
			OrderEventPublisher orderEventPublisher) {
		this.outboxEventRepository = outboxEventRepository;
		this.orderEventPublisher = orderEventPublisher;
	}

	public OrderCreatedEvent enqueueAndDispatchOrderCreated(OrderEventCommand command) {
		OrderCreatedEvent event = enqueueOrderCreated(command);
		dispatchPendingOutboxEvents();
		return event;
	}

	@Scheduled(initialDelayString = "${app.messaging.outbox-dispatch-initial-delay-ms:10000}",
			fixedDelayString = "${app.messaging.outbox-dispatch-delay-ms:30000}")
	public void dispatchPendingOutboxEvents() {
		outboxEventRepository.findTop25ByStatusOrderByCreatedAtAsc(STATUS_PENDING).forEach(this::dispatch);
	}

	private OrderCreatedEvent enqueueOrderCreated(OrderEventCommand command) {
		if (outboxEventRepository.existsByEventTypeAndAggregateId(EVENT_TYPE_ORDER_CREATED, command.orderId())) {
			return null;
		}
		Instant now = Instant.now();
		OrderCreatedEvent event = new OrderCreatedEvent(
				UUID.randomUUID().toString(),
				command.orderId(),
				command.userId(),
				command.customerEmail(),
				command.totalAmount(),
				command.currency(),
				now);
		outboxEventRepository.save(new OrderOutboxEventDocument(
				null,
				EVENT_TYPE_ORDER_CREATED,
				command.orderId(),
				event,
				STATUS_PENDING,
				0,
				null,
				now,
				now));
		return event;
	}

	private void dispatch(OrderOutboxEventDocument document) {
		try {
			orderEventPublisher.publish(document.payload());
			outboxEventRepository.save(new OrderOutboxEventDocument(
					document.id(),
					document.eventType(),
					document.aggregateId(),
					document.payload(),
					STATUS_DISPATCHED,
					document.attempts() + 1,
					null,
					document.createdAt(),
					Instant.now()));
		}
		catch (RuntimeException exception) {
			outboxEventRepository.save(new OrderOutboxEventDocument(
					document.id(),
					document.eventType(),
					document.aggregateId(),
					document.payload(),
					STATUS_PENDING,
					document.attempts() + 1,
					exception.getMessage(),
					document.createdAt(),
					Instant.now()));
		}
	}
}
