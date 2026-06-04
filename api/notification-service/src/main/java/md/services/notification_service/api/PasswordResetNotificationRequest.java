package md.services.notification_service.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Internal command used to send a password reset notification email.")
public record PasswordResetNotificationRequest(
        @Schema(description = "Authenticated identity id.", example = "auth-1") @NotBlank String authId,
        @Schema(description = "Recipient email address.", example = "customer@example.com") @Email @NotBlank String email,
        @Schema(description = "One-time password reset token.", example = "7f65d4ce-4cf4-42e0-b452-1b3471c1d746") @NotBlank String token,
        @Schema(description = "UTC timestamp when the reset token expires.", example = "2026-06-04T10:45:00Z") @NotNull Instant expiresAt) {
}