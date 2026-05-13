package md.services.order_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.RequestInterceptor;

@Configuration(proxyBeanMethods = false)
public class InternalFeignConfiguration {

	@Bean
	RequestInterceptor internalServiceTokenRequestInterceptor(
			@Value("${app.security.internal-service-token:modern-ecommerce-local-internal-token}") String internalServiceToken) {
		return template -> {
			template.header("X-Internal-Service", "order-service");
			template.header("X-Internal-Service-Token", internalServiceToken);
		};
	}
}
