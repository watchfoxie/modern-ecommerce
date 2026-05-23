package md.services.product_service;

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
		"PRODUCT_SERVICE_USERNAME=test-product-user",
		"PRODUCT_SERVICE_PASSWORD=test-product-password",
		"PRODUCT_MONGODB_URI=mongodb://localhost:27017/product-service-test",
		"PRODUCT_SERVICE_DB_NAME=product-service-test",
		"app.data.migrations.enabled=false",
		"app.startup.mongo-verification.enabled=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ProductOpenApiContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsExposeProductContractAndPagedSchema() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/products'].get").exists())
				.andExpect(jsonPath("$.paths['/products/search'].get").exists())
				.andExpect(jsonPath("$.paths['/products/{slug}'].get").exists())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"));
	}
}
