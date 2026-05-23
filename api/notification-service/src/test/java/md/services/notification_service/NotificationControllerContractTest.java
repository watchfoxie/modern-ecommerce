package md.services.notification_service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import md.services.notification_service.api.NotificationQueryController;
import md.services.notification_service.domain.NotificationRecord;
import md.services.notification_service.service.NotificationInboxStore;

class NotificationControllerContractTest {

	private NotificationInboxStore notificationInboxStore;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		notificationInboxStore = mock(NotificationInboxStore.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new NotificationQueryController(notificationInboxStore))
				.build();
	}

	@Test
	void internalDiagnosticsReturnRecentAndDeadLetterNotifications() throws Exception {
		when(notificationInboxStore.recentNotifications()).thenReturn(List.of(new NotificationRecord(
				"event-1", "order-1", "customer@example.com", "MAIL_SENT", "Consumed", Instant.parse("2026-05-01T12:00:00Z"))));
		when(notificationInboxStore.deadLetterNotifications()).thenReturn(List.of(new NotificationRecord(
				"event-2", "order-2", "customer@example.com", "DEAD_LETTER", "Retry exhausted", Instant.parse("2026-05-01T12:01:00Z"))));

		mockMvc.perform(get("/internal/notifications"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recentNotifications[0].status").value("MAIL_SENT"))
				.andExpect(jsonPath("$.deadLetterNotifications[0].status").value("DEAD_LETTER"));
	}
}
