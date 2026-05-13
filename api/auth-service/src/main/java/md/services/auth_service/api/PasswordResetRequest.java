package md.services.auth_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request used to start a password reset flow without account enumeration.")
public record PasswordResetRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email String email) {
}
