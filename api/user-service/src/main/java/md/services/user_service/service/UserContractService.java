package md.services.user_service.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import md.services.user_service.api.AddressDto;
import md.services.user_service.api.AddressRequest;
import md.services.user_service.api.CreateUserProfileRequest;
import md.services.user_service.api.PreferencesDto;
import md.services.user_service.api.UserProfileDto;
import md.services.user_service.api.UserProfileUpdateRequest;
import md.services.user_service.domain.UserProfileDocument;
import md.services.user_service.domain.UserProfileDocument.AddressDocument;
import md.services.user_service.domain.UserProfileDocument.PreferencesDocument;
import md.services.user_service.repository.UserProfileRepository;

@Service
public class UserContractService {

	private final UserProfileRepository userProfileRepository;

	public UserContractService(UserProfileRepository userProfileRepository) {
		this.userProfileRepository = userProfileRepository;
	}

	public UserProfileDto getCurrentUserProfile(String authId) {
		return toDto(getByAuthId(authId));
	}

	public UserProfileDto updateCurrentUserProfile(String authId, UserProfileUpdateRequest request) {
		UserProfileDocument current = getByAuthId(authId);
		UserProfileDocument updated = new UserProfileDocument(
				current.id(),
				current.authId(),
				current.email(),
				clean(request.firstName()),
				clean(request.lastName()),
				clean(request.phone()),
				request.birthDate(),
				orEmpty(current.addresses()),
				toDocument(request.preferences()),
				current.active(),
				current.createdAt(),
				Instant.now());
		return toDto(userProfileRepository.save(updated));
	}

	public UserProfileDto addAddress(String authId, AddressRequest request) {
		UserProfileDocument current = getByAuthId(authId);
		List<AddressDocument> addresses = new ArrayList<>(orEmpty(current.addresses()));
		addresses.add(toDocument(request));
		return toDto(saveWithAddresses(current, normalizeDefault(addresses)));
	}

	public UserProfileDto replaceAddress(String authId, int addressIndex, AddressRequest request) {
		UserProfileDocument current = getByAuthId(authId);
		List<AddressDocument> addresses = new ArrayList<>(orEmpty(current.addresses()));
		requireAddressIndex(addressIndex, addresses);
		addresses.set(addressIndex, toDocument(request));
		return toDto(saveWithAddresses(current, normalizeDefault(addresses)));
	}

	public void deleteAddress(String authId, int addressIndex) {
		UserProfileDocument current = getByAuthId(authId);
		List<AddressDocument> addresses = new ArrayList<>(orEmpty(current.addresses()));
		requireAddressIndex(addressIndex, addresses);
		addresses.remove(addressIndex);
		saveWithAddresses(current, normalizeDefault(addresses));
	}

	public UserProfileDto createInternalProfile(CreateUserProfileRequest request) {
		return userProfileRepository.findByAuthId(clean(request.authId()))
				.map(this::toDto)
				.orElseGet(() -> {
					Instant now = Instant.now();
					UserProfileDocument document = new UserProfileDocument(
							null,
							clean(request.authId()),
							clean(request.email()).toLowerCase(),
							clean(request.firstName()),
							clean(request.lastName()),
							clean(request.phone()),
							request.birthDate(),
							List.of(),
							new PreferencesDocument("ro", "MDL"),
							true,
							now,
							now);
					return toDto(userProfileRepository.save(document));
				});
	}

	public UserProfileDto findByAuthId(String authId) {
		return toDto(getByAuthId(authId));
	}

	public UserProfileDto findByEmail(String email) {
		return userProfileRepository.findByEmailIgnoreCase(clean(email))
				.map(this::toDto)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found."));
	}

	private UserProfileDocument getByAuthId(String authId) {
		return userProfileRepository.findByAuthId(clean(authId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found."));
	}

	private UserProfileDocument saveWithAddresses(UserProfileDocument current, List<AddressDocument> addresses) {
		return userProfileRepository.save(new UserProfileDocument(
				current.id(),
				current.authId(),
				current.email(),
				current.firstName(),
				current.lastName(),
				current.phone(),
				current.birthDate(),
				addresses,
				current.preferences(),
				current.active(),
				current.createdAt(),
				Instant.now()));
	}

	private void requireAddressIndex(int addressIndex, List<AddressDocument> addresses) {
		if (addressIndex >= addresses.size()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found.");
		}
	}

	private List<AddressDocument> normalizeDefault(List<AddressDocument> addresses) {
		if (addresses.isEmpty()) {
			return List.of();
		}

		int defaultIndex = -1;
		for (int index = 0; index < addresses.size(); index++) {
			if (addresses.get(index).isDefault()) {
				defaultIndex = index;
			}
		}
		if (defaultIndex < 0) {
			defaultIndex = 0;
		}

		List<AddressDocument> normalized = new ArrayList<>(addresses.size());
		for (int index = 0; index < addresses.size(); index++) {
			AddressDocument address = addresses.get(index);
			normalized.add(new AddressDocument(
					address.label(),
					address.street(),
					address.city(),
					address.district(),
					address.postalCode(),
					index == defaultIndex));
		}
		return List.copyOf(normalized);
	}

	private AddressDocument toDocument(AddressRequest request) {
		return new AddressDocument(
				clean(request.label()),
				clean(request.street()),
				clean(request.city()),
				clean(request.district()),
				clean(request.postalCode()),
				request.isDefault());
	}

	private PreferencesDocument toDocument(PreferencesDto preferences) {
		if (preferences == null) {
			return new PreferencesDocument("ro", "MDL");
		}
		return new PreferencesDocument(
				cleanOrDefault(preferences.language(), "ro"),
				cleanOrDefault(preferences.currency(), "MDL"));
	}

	private UserProfileDto toDto(UserProfileDocument document) {
		return new UserProfileDto(
				document.id(),
				document.authId(),
				document.email(),
				document.firstName(),
				document.lastName(),
				document.phone(),
				document.birthDate(),
				orEmpty(document.addresses()).stream().map(this::toDto).toList(),
				toDto(document.preferences()),
				toOffsetDateTime(document.createdAt()),
				toOffsetDateTime(document.updatedAt()));
	}

	private AddressDto toDto(AddressDocument address) {
		return new AddressDto(
				address.label(),
				address.street(),
				address.city(),
				address.district(),
				address.postalCode(),
				address.isDefault());
	}

	private PreferencesDto toDto(PreferencesDocument preferences) {
		if (preferences == null) {
			return new PreferencesDto("ro", "MDL");
		}
		return new PreferencesDto(preferences.language(), preferences.currency());
	}

	private OffsetDateTime toOffsetDateTime(Instant instant) {
		return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private List<AddressDocument> orEmpty(List<AddressDocument> addresses) {
		return addresses == null ? List.of() : addresses;
	}

	private String cleanOrDefault(String value, String defaultValue) {
		String cleaned = clean(value);
		return cleaned == null ? defaultValue : cleaned;
	}

	private String clean(String value) {
		if (value == null) {
			return null;
		}
		String cleaned = value.trim();
		return cleaned.isEmpty() ? null : cleaned;
	}

}
