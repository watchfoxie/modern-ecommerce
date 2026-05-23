package md.services.notification_service;

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
		"NOTIFICATION_MAIL_USERNAME=test@example.com",
		"NOTIFICATION_MAIL_PASSWORD=test-password",
		"spring.rabbitmq.dynamic=false",
		"spring.rabbitmq.listener.simple.auto-startup=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class NotificationOpenApiContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsHideInternalNotificationDiagnostics() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/internal/notifications']").doesNotExist());
	}
}
