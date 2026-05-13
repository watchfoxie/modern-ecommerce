package md.services.user_service.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public user profile representation owned by user-service.")
public record UserProfileDto(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String authId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
		String phone,
		LocalDate birthDate,
		List<AddressDto> addresses,
		PreferencesDto preferences,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt) {
}
