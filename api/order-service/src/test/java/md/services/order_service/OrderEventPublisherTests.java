package md.services.order_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fasterxml.jackson.databind.json.JsonMapper;

import md.services.order_service.config.OrderMessagingProperties;
import md.services.order_service.domain.OrderCreatedEvent;
import md.services.order_service.service.OrderEventCommand;
import md.services.order_service.service.OrderEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTests {

	@Mock
	private RabbitTemplate rabbitTemplate;

	@Captor
	private ArgumentCaptor<String> payloadCaptor;

	@Captor
	private ArgumentCaptor<MessagePostProcessor> postProcessorCaptor;

	private OrderEventPublisher publisher;

	@BeforeEach
	void setUp() {
		publisher = new OrderEventPublisher(
				rabbitTemplate,
				JsonMapper.builder().findAndAddModules().build(),
				new OrderMessagingProperties("modern-ecommerce.events", "modern-ecommerce.events.dlx", "order.created"));
	}

	@Test
	void publishOrderCreatedSendsJsonPayloadToConfiguredExchange() {
		OrderCreatedEvent event = publisher.publishOrderCreated(
				new OrderEventCommand("ORD-1001", "USR-2001", "customer@example.com", new BigDecimal("2499.99"), "EUR"));

		verify(rabbitTemplate).convertAndSend(
				eq("modern-ecommerce.events"),
				eq("order.created"),
				payloadCaptor.capture(),
				postProcessorCaptor.capture());

		assertThat(event.orderId()).isEqualTo("ORD-1001");
		assertThat(event.customerEmail()).isEqualTo("customer@example.com");
		assertThat(payloadCaptor.getValue()).contains("\"orderId\":\"ORD-1001\"");
		assertThat(payloadCaptor.getValue()).contains("\"currency\":\"EUR\"");

		Message message = postProcessorCaptor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
		assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId());
		assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo("ORD-1001");
		assertThat((Object) message.getMessageProperties().getHeader("eventType")).isEqualTo("order.created");
		assertThat((Object) message.getMessageProperties().getHeader("eventId")).isEqualTo(event.eventId());
		assertThat((Object) message.getMessageProperties().getHeader("eventVersion")).isEqualTo("1");
		assertThat(message.getMessageProperties().getTimestamp()).isEqualTo(java.util.Date.from(event.occurredAt()));
	}

}
