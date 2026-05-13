package md.services.auth_service.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public representation of an authentication identity without password hash.")
public record AuthIdentityDto(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
}
