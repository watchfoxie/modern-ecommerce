package md.services.notification_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.json.JsonMapper;

import md.services.notification_service.domain.OrderCreatedEvent;
import md.services.notification_service.messaging.OrderCreatedEventListener;
import md.services.notification_service.service.NotificationDispatchService;
import md.services.notification_service.service.NotificationInboxStore;

@ExtendWith(MockitoExtension.class)
class OrderCreatedEventListenerTests {

	@Mock
	private NotificationDispatchService notificationDispatchService;

	@Test
	void handleOrderCreatedRecordsSuccessfulDelivery() throws Exception {
		NotificationInboxStore notificationInboxStore = new NotificationInboxStore();
		OrderCreatedEventListener listener = new OrderCreatedEventListener(
				JsonMapper.builder().findAndAddModules().build(),
				notificationDispatchService,
				notificationInboxStore);

		when(notificationDispatchService.dispatchOrderCreatedNotification(any())).thenReturn("MAIL_SENT");

		listener.handleOrderCreated(JsonMapper.builder().findAndAddModules().build().writeValueAsString(sampleEvent()));

		verify(notificationDispatchService).dispatchOrderCreatedNotification(any());
		assertThat(notificationInboxStore.recentNotifications()).hasSize(1);
		assertThat(notificationInboxStore.recentNotifications().get(0).status()).isEqualTo("MAIL_SENT");
	}

	@Test
	void handleDeadLetterRecordsDeadLetterEntry() throws Exception {
		NotificationInboxStore notificationInboxStore = new NotificationInboxStore();
		OrderCreatedEventListener listener = new OrderCreatedEventListener(
				JsonMapper.builder().findAndAddModules().build(),
				notificationDispatchService,
				notificationInboxStore);

		listener.handleDeadLetter(JsonMapper.builder().findAndAddModules().build().writeValueAsString(sampleEvent()));

		assertThat(notificationInboxStore.deadLetterNotifications()).hasSize(1);
		assertThat(notificationInboxStore.deadLetterNotifications().get(0).status()).isEqualTo("DEAD_LETTER");
	}

	@Test
	void handleOrderCreatedIgnoresDuplicateEventIds() throws Exception {
		NotificationInboxStore notificationInboxStore = new NotificationInboxStore();
		OrderCreatedEventListener listener = new OrderCreatedEventListener(
				JsonMapper.builder().findAndAddModules().build(),
				notificationDispatchService,
				notificationInboxStore);

		when(notificationDispatchService.dispatchOrderCreatedNotification(any())).thenReturn("MAIL_SENT");

		String payload = JsonMapper.builder().findAndAddModules().build().writeValueAsString(sampleEvent());
		listener.handleOrderCreated(payload);
		listener.handleOrderCreated(payload);

		verify(notificationDispatchService, times(1)).dispatchOrderCreatedNotification(any());
		assertThat(notificationInboxStore.recentNotifications()).hasSize(2);
		assertThat(notificationInboxStore.recentNotifications().get(0).status()).isEqualTo("DUPLICATE_IGNORED");
		assertThat(notificationInboxStore.recentNotifications().get(1).status()).isEqualTo("MAIL_SENT");
	}

	private OrderCreatedEvent sampleEvent() {
		return new OrderCreatedEvent(
				"evt-1",
				"ORD-1001",
				"USR-2001",
				"customer@example.com",
				new BigDecimal("2499.99"),
				"EUR",
				Instant.parse("2026-04-02T11:45:00Z"));
	}

}
