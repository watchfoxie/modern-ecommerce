package md.services.order_service.service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import md.services.order_service.config.OrderMessagingProperties;
import md.services.order_service.domain.OrderCreatedEvent;

@Service
public class OrderEventPublisher {

	private static final String ORDER_CREATED_EVENT_TYPE = "order.created";

	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;
	private final OrderMessagingProperties properties;

	public OrderEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
			OrderMessagingProperties properties) {
		this.rabbitTemplate = rabbitTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	public OrderCreatedEvent publishOrderCreated(OrderEventCommand command) {
		OrderCreatedEvent event = new OrderCreatedEvent(
				UUID.randomUUID().toString(),
				command.orderId(),
				command.userId(),
				command.customerEmail(),
				command.totalAmount(),
				command.currency(),
				Instant.now());

		publish(event);

		return event;
	}

	public void publish(OrderCreatedEvent event) {
		rabbitTemplate.convertAndSend(
				properties.exchange(),
				properties.orderCreatedRoutingKey(),
				serialize(event),
				enrichMessage(event));
	}

	private String serialize(OrderCreatedEvent event) {
		try {
			return objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not serialize order.created event payload.", exception);
		}
	}

	private MessagePostProcessor enrichMessage(OrderCreatedEvent event) {
		return message -> {
			message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
			message.getMessageProperties().setMessageId(event.eventId());
			message.getMessageProperties().setCorrelationId(event.orderId());
			message.getMessageProperties().setTimestamp(Date.from(event.occurredAt()));
			message.getMessageProperties().setHeader("eventType", ORDER_CREATED_EVENT_TYPE);
			message.getMessageProperties().setHeader("eventId", event.eventId());
			message.getMessageProperties().setHeader("eventVersion", "1");
			return message;
		};
	}

}
