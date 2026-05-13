package md.services.order_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	private static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI orderServiceOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("MEc Order Service API")
						.version("v1")
						.description("Contract for checkout, order history, and administrative order status updates."))
				.addServersItem(new Server().url("/").description("Order service internal base URL"))
				.addTagsItem(new Tag().name("Orders").description("Order command and query contract"))
				.externalDocs(new ExternalDocumentation()
						.description("Project architecture reference")
						.url("project_architecture_reference.md"))
				.components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
						.name(BEARER_AUTH)
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

}
