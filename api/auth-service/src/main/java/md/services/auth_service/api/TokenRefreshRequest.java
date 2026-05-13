package md.services.auth_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request used to exchange a refresh token for a fresh token pair.")
public record TokenRefreshRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String refreshToken) {
}
