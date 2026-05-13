package md.services.auth_service.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bearer token response returned after authentication or refresh.")
public record AuthTokenResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String refreshToken,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expiresIn,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "Bearer") String tokenType) {
}
