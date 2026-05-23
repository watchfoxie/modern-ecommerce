package md.services.order_service;

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
		"ORDER_MONGODB_URI=mongodb://localhost:27017/order-service-test",
		"ORDER_SERVICE_DB_NAME=order-service-test",
		"app.data.migrations.enabled=false",
		"app.startup.mongo-verification.enabled=false",
		"spring.rabbitmq.dynamic=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OrderOpenApiContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsExposeOrderContractAndBearerScheme() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/orders'].post").exists())
				.andExpect(jsonPath("$.paths['/orders'].get").exists())
				.andExpect(jsonPath("$.paths['/orders/{orderId}/status'].patch").exists())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"));
	}
}
