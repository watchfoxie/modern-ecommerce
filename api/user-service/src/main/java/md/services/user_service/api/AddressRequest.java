package md.services.user_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request for creating or replacing an embedded delivery address.")
public record AddressRequest(
		@Size(max = 80) String label,
		@NotBlank @Size(max = 160) String street,
		@NotBlank @Size(max = 80) String city,
		@NotBlank @Size(max = 80) String district,
		@Size(max = 20) String postalCode,
		boolean isDefault) {
}
