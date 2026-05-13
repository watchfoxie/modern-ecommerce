package md.services.order_service.config;

import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class ActuatorInfoContributorConfiguration {

	@Bean
	InfoContributor orderServiceInfoContributor(Environment environment, @Value("${server.port}") String serverPort) {
		return builder -> builder
				.withDetail("app", appDetails(environment, serverPort))
				.withDetail("service",
						serviceDetails("Order intake and event publication", List.of("eureka", "mongodb", "rabbitmq")));
	}

	private LinkedHashMap<String, Object> appDetails(Environment environment, String serverPort) {
		LinkedHashMap<String, Object> app = new LinkedHashMap<>();
		app.put("name", environment.getProperty("spring.application.name", "order-service"));
		app.put("port", serverPort);
		app.put("profiles", activeProfiles(environment));
		return app;
	}

	private LinkedHashMap<String, Object> serviceDetails(String role, List<String> integrations) {
		LinkedHashMap<String, Object> service = new LinkedHashMap<>();
		service.put("role", role);
		service.put("integrations", integrations);
		return service;
	}

	private List<String> activeProfiles(Environment environment) {
		String[] activeProfiles = environment.getActiveProfiles();
		if (activeProfiles.length > 0) {
			return List.of(activeProfiles);
		}
		String[] defaultProfiles = environment.getDefaultProfiles();
		return (defaultProfiles.length == 0) ? List.of("default") : List.of(defaultProfiles);
	}

}
