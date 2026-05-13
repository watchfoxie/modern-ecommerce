package md.services.user_service.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User interface and commerce preferences.")
public record PreferencesDto(
		String language,
		String currency) {
}
