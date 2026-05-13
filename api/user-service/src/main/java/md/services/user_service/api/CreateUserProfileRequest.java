package md.services.user_service.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Internal request used when auth-service provisions a matching user profile.")
public record CreateUserProfileRequest(
		@NotBlank String authId,
		@NotBlank @Email String email,
		@NotBlank @Size(max = 80) String firstName,
		@NotBlank @Size(max = 80) String lastName,
		@Size(max = 30) String phone,
		LocalDate birthDate) {
}
