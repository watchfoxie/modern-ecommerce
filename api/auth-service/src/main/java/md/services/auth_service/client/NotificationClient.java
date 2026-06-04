package md.services.auth_service.client;

import java.time.Instant;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import md.services.auth_service.domain.AuthUserDocument;

@FeignClient(name = "notification-service")
public interface NotificationClient {

    @PostMapping("/internal/notifications/password-reset")
    void sendPasswordReset(
            @RequestHeader("X-Internal-Service") String internalService,
            @RequestHeader("X-Internal-Service-Token") String internalServiceToken,
            @RequestBody PasswordResetNotificationRequest request);

    record PasswordResetNotificationRequest(String authId, String email, String token, Instant expiresAt) {

        public static PasswordResetNotificationRequest from(AuthUserDocument user) {
            return new PasswordResetNotificationRequest(
                    user.id(),
                    user.email(),
                    user.passwordResetToken(),
                    user.passwordResetExpiry());
        }
    }
}