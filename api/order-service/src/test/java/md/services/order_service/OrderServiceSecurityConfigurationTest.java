package md.services.order_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"ORDER_MONGODB_URI=mongodb://localhost:27017/order-service-test",
		"ORDER_SERVICE_DB_NAME=order-service-test",
		"app.data.migrations.enabled=false",
		"spring.rabbitmq.dynamic=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class OrderServiceSecurityConfigurationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rejectsBusinessRequestsWithoutGatewayIdentity() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isForbidden());
	}

	@Test
	void acceptsGatewayInternalIdentityWithoutBasicAuthChallenge() throws Exception {
		mockMvc.perform(get("/")
				.header("X-Internal-Service-Token", "modern-ecommerce-local-internal-token")
				.header("X-User-Id", "user-1")
				.header("X-User-Roles", "ROLE_USER"))
				.andExpect(status().isNotFound());
	}

	@Test
	void exposesReadinessHealthWithoutBasicAuthChallenge() throws Exception {
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk());
	}

	@Test
	void exposesActuatorInfoPayloadWithoutBasicAuthChallenge() throws Exception {
		mockMvc.perform(get("/actuator/info"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.app.name").value("order-service"))
				.andExpect(jsonPath("$.app.port").value("8086"))
				.andExpect(jsonPath("$.service.role").value("Order intake and event publication"))
				.andExpect(jsonPath("$.service.integrations[0]").value("eureka"))
				.andExpect(jsonPath("$.service.integrations[1]").value("mongodb"))
				.andExpect(jsonPath("$.service.integrations[2]").value("rabbitmq"));
	}

}
