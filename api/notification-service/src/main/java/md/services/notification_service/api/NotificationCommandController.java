package md.services.notification_service.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import md.services.notification_service.service.NotificationDispatchService;

@Hidden
@RestController
@RequestMapping("/internal/notifications")
public class NotificationCommandController {

    private final NotificationDispatchService notificationDispatchService;

    public NotificationCommandController(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> sendPasswordResetNotification(
            @Valid @RequestBody PasswordResetNotificationRequest request) {
        notificationDispatchService.dispatchPasswordResetNotification(request);
        return ResponseEntity.noContent().build();
    }

}