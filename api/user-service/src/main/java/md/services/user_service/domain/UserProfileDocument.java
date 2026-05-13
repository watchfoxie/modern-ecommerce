package md.services.user_service.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public record UserProfileDocument(
		@Id String id,
		String authId,
		String email,
		String firstName,
		String lastName,
		String phone,
		LocalDate birthDate,
		List<AddressDocument> addresses,
		PreferencesDocument preferences,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {

	public record AddressDocument(
			String label,
			String street,
			String city,
			String district,
			String postalCode,
			boolean isDefault) {
	}

	public record PreferencesDocument(
			String language,
			String currency) {
	}
}
