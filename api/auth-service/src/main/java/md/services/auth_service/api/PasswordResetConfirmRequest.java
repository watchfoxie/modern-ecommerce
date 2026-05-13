package md.services.auth_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request used to confirm a password reset with a one-time token.")
public record PasswordResetConfirmRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String token,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 8, max = 128) String newPassword) {
}
