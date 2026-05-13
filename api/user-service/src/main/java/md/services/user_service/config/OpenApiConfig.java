package md.services.user_service.config;

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
	OpenAPI userServiceOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("MEc User Service API")
						.version("v1")
						.description("Contract for customer profile, addresses, and interface preferences."))
				.addServersItem(new Server().url("/").description("User service internal base URL"))
				.addTagsItem(new Tag().name("User profiles").description("Authenticated user profile contract"))
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
