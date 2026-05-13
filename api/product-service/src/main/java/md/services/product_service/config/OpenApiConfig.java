package md.services.product_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	@Bean
	OpenAPI productServiceOpenApi() {
		String bearerAuth = "bearerAuth";
		return new OpenAPI()
				.info(new Info()
						.title("MEc Product Service API")
						.version("0.0.1")
						.description("Contract for product catalog, search, promotions and administration."))
				.externalDocs(new ExternalDocumentation()
						.description("Project architecture reference")
						.url("project_architecture_reference.md"))
				.components(new Components().addSecuritySchemes(bearerAuth,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(bearerAuth));
	}

}
