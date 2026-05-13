package md.services.user_service.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Embedded delivery address associated with a user profile.")
public record AddressDto(
		String label,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String street,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String city,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String district,
		String postalCode,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean isDefault) {
}
