package md.services.notification_service.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import md.services.notification_service.domain.NotificationRecord;
import md.services.notification_service.service.NotificationInboxStore;

@Hidden
@RestController
@RequestMapping("/internal/notifications")
public class NotificationQueryController {

	private final NotificationInboxStore notificationInboxStore;

	public NotificationQueryController(NotificationInboxStore notificationInboxStore) {
		this.notificationInboxStore = notificationInboxStore;
	}

	@GetMapping
	public NotificationOverview overview() {
		return new NotificationOverview(
				notificationInboxStore.recentNotifications(),
				notificationInboxStore.deadLetterNotifications());
	}

	public record NotificationOverview(
			List<NotificationRecord> recentNotifications,
			List<NotificationRecord> deadLetterNotifications) {
	}

}
