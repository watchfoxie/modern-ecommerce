package md.services.auth_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"AUTH_SERVICE_USERNAME=test-auth-user",
		"AUTH_SERVICE_PASSWORD=test-auth-password",
		"AUTH_MONGODB_URI=mongodb://localhost:27017/auth-service-test",
		"AUTH_SERVICE_DB_NAME=auth-service-test",
		"app.data.migrations.enabled=false",
		"app.startup.mongo-verification.enabled=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthOpenApiContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsExposeAuthContractAndBearerScheme() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/sign-up'].post").exists())
				.andExpect(jsonPath("$.paths['/sign-in'].post").exists())
				.andExpect(jsonPath("$.paths['/password-reset/request'].post").exists())
				.andExpect(jsonPath("$.paths['/password-reset/confirm'].post").exists())
				.andExpect(jsonPath("$.paths['/token/refresh'].post").exists())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"));
	}
}
