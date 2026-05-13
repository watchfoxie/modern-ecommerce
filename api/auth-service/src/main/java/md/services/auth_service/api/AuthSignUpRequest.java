package md.services.auth_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Public request used to create an authentication identity.")
public record AuthSignUpRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 2, max = 80) String firstName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 2, max = 80) String lastName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email String email,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 8, max = 128) String password) {
}
