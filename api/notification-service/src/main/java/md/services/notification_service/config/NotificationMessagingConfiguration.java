package md.services.notification_service.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationMessagingProperties.class)
public class NotificationMessagingConfiguration {

	@Bean
	Declarables notificationMessagingTopology(NotificationMessagingProperties properties) {
		TopicExchange mainExchange = new TopicExchange(properties.exchange(), true, false);
		TopicExchange deadLetterExchange = new TopicExchange(properties.deadLetterExchange(), true, false);

		var orderCreatedQueue = QueueBuilder.durable(properties.orderCreatedQueue())
				.withArgument("x-dead-letter-exchange", properties.deadLetterExchange())
				.withArgument("x-dead-letter-routing-key", properties.orderCreatedDlqRoutingKey())
				.build();
		var orderCreatedDlqQueue = QueueBuilder.durable(properties.orderCreatedDlqQueue()).build();

		return new Declarables(
				mainExchange,
				deadLetterExchange,
				orderCreatedQueue,
				orderCreatedDlqQueue,
				BindingBuilder.bind(orderCreatedQueue).to(mainExchange).with(properties.orderCreatedRoutingKey()),
				BindingBuilder.bind(orderCreatedDlqQueue).to(deadLetterExchange).with(properties.orderCreatedDlqRoutingKey()));
	}

}
