package md.services.user_service;

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
		"USER_SERVICE_USERNAME=test-user",
		"USER_SERVICE_PASSWORD=test-user-password",
		"USER_MONGODB_URI=mongodb://localhost:27017/user-service-test",
		"USER_SERVICE_DB_NAME=user-service-test",
		"app.data.migrations.enabled=false",
		"app.startup.mongo-verification.enabled=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class UserOpenApiContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsExposeUserContractAndHideInternalLookup() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/users/me'].get").exists())
				.andExpect(jsonPath("$.paths['/users/me/addresses'].post").exists())
				.andExpect(jsonPath("$.paths['/users/internal/by-auth/{authId}']").doesNotExist())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"));
	}
}
