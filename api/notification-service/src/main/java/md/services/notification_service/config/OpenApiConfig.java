package md.services.notification_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	@Bean
	OpenAPI notificationServiceOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("MEc Notification Service Internal Diagnostics")
						.version("v1")
						.description("Notification-service is event-driven. REST diagnostics are internal and hidden from the public API contract."))
				.addServersItem(new Server().url("/").description("Notification service internal base URL"))
				.externalDocs(new ExternalDocumentation()
						.description("Project architecture reference")
						.url("project_architecture_reference.md"));
	}

}
