package md.services.order_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.messaging")
public record OrderMessagingProperties(
		@NotBlank String exchange,
		@NotBlank String deadLetterExchange,
		@NotBlank String orderCreatedRoutingKey) {
}
