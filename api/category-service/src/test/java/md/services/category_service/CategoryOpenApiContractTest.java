package md.services.category_service;

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
		"CATEGORY_SERVICE_USERNAME=test-category-user",
		"CATEGORY_SERVICE_PASSWORD=test-category-password",
		"CATEGORY_MONGODB_URI=mongodb://localhost:27017/category-service-test",
		"CATEGORY_SERVICE_DB_NAME=category-service-test",
		"app.data.migrations.enabled=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CategoryOpenApiContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsExposeCategoryContractAndBearerScheme() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/categories'].get").exists())
				.andExpect(jsonPath("$.paths['/categories'].post").exists())
				.andExpect(jsonPath("$.paths['/categories/{slug}'].get").exists())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"));
	}
}
