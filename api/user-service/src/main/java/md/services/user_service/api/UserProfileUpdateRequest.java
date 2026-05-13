package md.services.user_service.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request for updating the authenticated user's profile.")
public record UserProfileUpdateRequest(
		@NotBlank @Size(max = 80) String firstName,
		@NotBlank @Size(max = 80) String lastName,
		@Size(max = 30) @Pattern(regexp = "^[+0-9()\\-\\s]*$", message = "must be a phone-like value") String phone,
		LocalDate birthDate,
		@Valid PreferencesDto preferences) {
}
