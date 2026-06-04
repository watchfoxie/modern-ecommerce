package md.services.notification_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import md.services.notification_service.api.PasswordResetNotificationRequest;
import md.services.notification_service.domain.OrderCreatedEvent;

@Service
public class NotificationDispatchService {

	private final JavaMailSender javaMailSender;
	private final boolean mailEnabled;
	private final String senderAddress;

	public NotificationDispatchService(JavaMailSender javaMailSender,
			@Value("${notification.mail.enabled:false}") boolean mailEnabled,
			@Value("${spring.mail.username:}") String senderAddress,
			@Value("${spring.mail.password:}") String senderPassword) {
		this.javaMailSender = javaMailSender;
		this.mailEnabled = mailEnabled;
		this.senderAddress = senderAddress;

		boolean senderConfigured = senderAddress != null && !senderAddress.isBlank();
		boolean passwordConfigured = senderPassword != null && !senderPassword.isBlank();
		if (!mailEnabled && senderConfigured && passwordConfigured) {
			throw new IllegalStateException(
					"notification.mail.enabled must be true when spring.mail.username and spring.mail.password are configured.");
		}

		if (mailEnabled) {
			if (senderAddress == null || senderAddress.isBlank()) {
				throw new IllegalStateException(
						"spring.mail.username must be configured when notification.mail.enabled=true.");
			}
			if (senderPassword == null || senderPassword.isBlank()) {
				throw new IllegalStateException(
						"spring.mail.password must be configured when notification.mail.enabled=true.");
			}
		}
	}

	public String dispatchPasswordResetNotification(PasswordResetNotificationRequest request) {
		if (!mailEnabled) {
			return "MAIL_DISABLED";
		}

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(senderAddress);
		message.setTo(request.email());
		message.setSubject("Password reset requested");
		message.setText("""
				We received a request to reset the password for your account.

				Auth ID: %s
				Reset token: %s
				Expires at: %s

				Use the token on the password reset page in modern-ecommerce.
				If you did not request this change, you can ignore this email.
				""".formatted(
				request.authId(),
				request.token(),
				request.expiresAt()));

		javaMailSender.send(message);
		return "MAIL_SENT";
	}

	public String dispatchOrderCreatedNotification(OrderCreatedEvent event) {
		if (!mailEnabled) {
			return "MAIL_DISABLED";
		}

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(senderAddress);
		message.setTo(event.customerEmail());
		message.setSubject("Order " + event.orderId() + " accepted");
		message.setText("""
				Thank you for your order.

				Order ID: %s
				User ID: %s
				Total: %s %s
				Event ID: %s
				Occurred at: %s
				""".formatted(
				event.orderId(),
				event.userId(),
				event.totalAmount(),
				event.currency(),
				event.eventId(),
				event.occurredAt()));

		javaMailSender.send(message);
		return "MAIL_SENT";
	}

}
