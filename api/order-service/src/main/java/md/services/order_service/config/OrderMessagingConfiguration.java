package md.services.order_service.config;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrderMessagingProperties.class)
public class OrderMessagingConfiguration {

	@Bean
	Declarables orderMessagingTopology(OrderMessagingProperties properties) {
		return new Declarables(
				new TopicExchange(properties.exchange(), true, false),
				new TopicExchange(properties.deadLetterExchange(), true, false));
	}

}
