package md.services.notification_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"NOTIFICATION_MAIL_USERNAME=test@example.com",
		"NOTIFICATION_MAIL_PASSWORD=test-password",
		"spring.rabbitmq.dynamic=false",
		"spring.rabbitmq.listener.simple.auto-startup=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class NotificationServiceSecurityConfigurationTest {

	private static final String INTERNAL_TOKEN = "modern-ecommerce-local-internal-token";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rejectsInternalNotificationsWithoutInternalServiceToken() throws Exception {
		mockMvc.perform(get("/internal/notifications"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void exposesInternalNotificationsWithInternalServiceToken() throws Exception {
		mockMvc.perform(get("/internal/notifications")
				.header("X-Internal-Service-Token", INTERNAL_TOKEN))
				.andExpect(status().isOk());
	}

	@Test
	void exposesActuatorInfoWithoutBasicAuthChallenge() throws Exception {
		mockMvc.perform(get("/actuator/info"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.app.name").value("notification-service"))
				.andExpect(jsonPath("$.app.port").value("8087"))
				.andExpect(jsonPath("$.service.role").value("Notification consumption and inspection"))
				.andExpect(jsonPath("$.service.integrations[0]").value("eureka"))
				.andExpect(jsonPath("$.service.integrations[1]").value("rabbitmq"))
				.andExpect(jsonPath("$.service.integrations[2]").value("smtp"));
	}

}
