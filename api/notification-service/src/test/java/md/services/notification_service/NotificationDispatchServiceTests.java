package md.services.notification_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import md.services.notification_service.api.PasswordResetNotificationRequest;
import md.services.notification_service.domain.OrderCreatedEvent;
import md.services.notification_service.service.NotificationDispatchService;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTests {

	@Mock
	private JavaMailSender javaMailSender;

	@Test
	void dispatchOrderCreatedNotificationSkipsMailWhenDisabled() {
		NotificationDispatchService service = new NotificationDispatchService(javaMailSender, false, "", "");

		String status = service.dispatchOrderCreatedNotification(sampleEvent());

		assertThat(status).isEqualTo("MAIL_DISABLED");
		verifyNoInteractions(javaMailSender);
	}

	@Test
	void dispatchOrderCreatedNotificationSendsMailWhenEnabled() {
		NotificationDispatchService service = new NotificationDispatchService(
				javaMailSender,
				true,
				"sales@example.com",
				"app-password");

		String status = service.dispatchOrderCreatedNotification(sampleEvent());

		assertThat(status).isEqualTo("MAIL_SENT");
		verify(javaMailSender).send(any(SimpleMailMessage.class));
	}

	@Test
	void dispatchPasswordResetNotificationSendsMailWhenEnabled() {
		NotificationDispatchService service = new NotificationDispatchService(
				javaMailSender,
				true,
				"sales@example.com",
				"app-password");

		String status = service.dispatchPasswordResetNotification(samplePasswordResetRequest());

		assertThat(status).isEqualTo("MAIL_SENT");
		verify(javaMailSender).send(any(SimpleMailMessage.class));
	}

	@Test
	void dispatchOrderCreatedNotificationFailsFastWhenSenderAddressMissing() {
		assertThatThrownBy(() -> new NotificationDispatchService(javaMailSender, true, "", "app-password"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("spring.mail.username");
	}

	@Test
	void dispatchOrderCreatedNotificationFailsFastWhenSenderPasswordMissing() {
		assertThatThrownBy(() -> new NotificationDispatchService(javaMailSender, true, "sales@example.com", ""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("spring.mail.password");
	}

	@Test
	void dispatchOrderCreatedNotificationFailsFastWhenCredentialsExistButMailIsDisabled() {
		assertThatThrownBy(
				() -> new NotificationDispatchService(javaMailSender, false, "sales@example.com", "app-password"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("notification.mail.enabled must be true");
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

	private PasswordResetNotificationRequest samplePasswordResetRequest() {
		return new PasswordResetNotificationRequest(
				"auth-1",
				"customer@example.com",
				"reset-token",
				Instant.parse("2026-06-04T10:45:00Z"));
	}

}
