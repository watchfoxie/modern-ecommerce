package md.services.notification_service.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import md.services.notification_service.domain.OrderCreatedEvent;
import md.services.notification_service.service.NotificationDispatchService;
import md.services.notification_service.service.NotificationInboxStore;

@Component
public class OrderCreatedEventListener {

	private final ObjectMapper objectMapper;
	private final NotificationDispatchService notificationDispatchService;
	private final NotificationInboxStore notificationInboxStore;

	public OrderCreatedEventListener(ObjectMapper objectMapper,
			NotificationDispatchService notificationDispatchService,
			NotificationInboxStore notificationInboxStore) {
		this.objectMapper = objectMapper;
		this.notificationDispatchService = notificationDispatchService;
		this.notificationInboxStore = notificationInboxStore;
	}

	@RabbitListener(queues = "${app.messaging.order-created-queue}")
	public void handleOrderCreated(String payload) {
		OrderCreatedEvent event = deserialize(payload);
		if (!notificationInboxStore.markProcessedIfNew(event.eventId())) {
			notificationInboxStore.recordDuplicate(event, "Ignored duplicate order.created delivery.");
			return;
		}
		String dispatchStatus = notificationDispatchService.dispatchOrderCreatedNotification(event);
		notificationInboxStore.recordDelivered(event, dispatchStatus, "Consumed from order.created queue.");
	}

	@RabbitListener(queues = "${app.messaging.order-created-dlq-queue}")
	public void handleDeadLetter(String payload) {
		OrderCreatedEvent event;
		try {
			event = deserialize(payload);
		}
		catch (IllegalArgumentException exception) {
			notificationInboxStore.recordMalformedDeadLetter(payload,
					"Dead-letter payload could not be deserialized: " + exception.getMessage());
			return;
		}
		if (!notificationInboxStore.markProcessedIfNew(event.eventId())) {
			notificationInboxStore.recordDuplicate(event, "Ignored duplicate dead-letter delivery.");
			return;
		}
		notificationInboxStore.recordDeadLetter(event, "Moved to dead-letter queue after retry exhaustion.");
	}

	private OrderCreatedEvent deserialize(String payload) {
		try {
			return objectMapper.readValue(payload, OrderCreatedEvent.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Could not deserialize order.created event payload.", exception);
		}
	}

}
