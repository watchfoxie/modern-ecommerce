package md.services.notification_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

class NotificationMessagingConfigurationTests {

	@Test
	void declaresOrderCreatedDeadLetterTopologyWithTtl() {
		NotificationMessagingProperties properties = new NotificationMessagingProperties(
				"modern-ecommerce.events",
				"modern-ecommerce.events.dlx",
				"order.created",
				"notification.order-created.v1",
				"notification.order-created.v1.dlq",
				"notification.order-created.v1.dlq",
				1000,
				60000);

		var declarables = new NotificationMessagingConfiguration().notificationMessagingTopology(properties);

		Queue orderCreatedQueue = declarables.getDeclarablesByType(Queue.class).stream()
				.filter(queue -> queue.getName().equals("notification.order-created.v1"))
				.findFirst()
				.orElseThrow();
		Queue deadLetterQueue = declarables.getDeclarablesByType(Queue.class).stream()
				.filter(queue -> queue.getName().equals("notification.order-created.v1.dlq"))
				.findFirst()
				.orElseThrow();

		assertThat(orderCreatedQueue.getArguments())
				.containsEntry("x-dead-letter-exchange", "modern-ecommerce.events.dlx")
				.containsEntry("x-dead-letter-routing-key", "notification.order-created.v1.dlq")
				.containsEntry("x-message-ttl", 1000);
		assertThat(deadLetterQueue.getArguments()).containsEntry("x-message-ttl", 60000);
	}
}
