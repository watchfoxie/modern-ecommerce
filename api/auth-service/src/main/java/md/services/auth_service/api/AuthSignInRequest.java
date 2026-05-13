package md.services.auth_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Public request used to authenticate a registered user.")
public record AuthSignInRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email String email,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String password) {
}
