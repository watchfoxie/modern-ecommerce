package md.services.user_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import md.services.user_service.api.AddressRequest;
import md.services.user_service.api.CreateUserProfileRequest;
import md.services.user_service.api.UserProfileDto;
import md.services.user_service.domain.UserProfileDocument;
import md.services.user_service.domain.UserProfileDocument.AddressDocument;
import md.services.user_service.domain.UserProfileDocument.PreferencesDocument;
import md.services.user_service.repository.UserProfileRepository;
import md.services.user_service.service.UserContractService;

@ExtendWith(MockitoExtension.class)
class UserContractServiceTests {

	@Mock
	private UserProfileRepository userProfileRepository;

	@Test
	void createInternalProfilePersistsMongoUserDocument() {
		UserContractService service = new UserContractService(userProfileRepository);
		when(userProfileRepository.findByAuthId("auth-1")).thenReturn(Optional.empty());
		when(userProfileRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

		UserProfileDto profile = service.createInternalProfile(new CreateUserProfileRequest(
				" auth-1 ",
				" USER@example.com ",
				" Ana ",
				" Popescu ",
				" +373 ",
				null));

		assertThat(profile.id()).isEqualTo("user-1");
		assertThat(profile.authId()).isEqualTo("auth-1");
		assertThat(profile.email()).isEqualTo("user@example.com");
		assertThat(profile.preferences().currency()).isEqualTo("MDL");
	}

	@Test
	void addAddressNormalizesSingleDefaultAddress() {
		UserContractService service = new UserContractService(userProfileRepository);
		UserProfileDocument existing = profile(List.of(
				new AddressDocument("Home", "Street 1", "Chisinau", "Centru", "2001", true)));
		when(userProfileRepository.findByAuthId("auth-1")).thenReturn(Optional.of(existing));
		when(userProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		UserProfileDto profile = service.addAddress("auth-1", new AddressRequest(
				"Office",
				"Street 2",
				"Chisinau",
				"Riscani",
				"2002",
				true));

		assertThat(profile.addresses()).hasSize(2);
		assertThat(profile.addresses().get(0).isDefault()).isFalse();
		assertThat(profile.addresses().get(1).isDefault()).isTrue();
	}

	@Test
	void deleteDefaultAddressPromotesFirstRemainingAddress() {
		UserContractService service = new UserContractService(userProfileRepository);
		UserProfileDocument existing = profile(List.of(
				new AddressDocument("Home", "Street 1", "Chisinau", "Centru", "2001", true),
				new AddressDocument("Office", "Street 2", "Chisinau", "Riscani", "2002", false)));
		when(userProfileRepository.findByAuthId("auth-1")).thenReturn(Optional.of(existing));
		when(userProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.deleteAddress("auth-1", 0);

		ArgumentCaptor<UserProfileDocument> captor = ArgumentCaptor.forClass(UserProfileDocument.class);
		verify(userProfileRepository).save(captor.capture());

		assertThat(captor.getValue().addresses()).hasSize(1);
		assertThat(captor.getValue().addresses().get(0).isDefault()).isTrue();
	}

	private UserProfileDocument profile(List<AddressDocument> addresses) {
		Instant now = Instant.parse("2026-04-02T11:45:00Z");
		return new UserProfileDocument(
				"user-1",
				"auth-1",
				"user@example.com",
				"Ana",
				"Popescu",
				null,
				null,
				addresses,
				new PreferencesDocument("ro", "MDL"),
				true,
				now,
				now);
	}

	private UserProfileDocument withId(UserProfileDocument document) {
		Instant now = Instant.parse("2026-04-02T11:45:00Z");
		return new UserProfileDocument(
				"user-1",
				document.authId(),
				document.email(),
				document.firstName(),
				document.lastName(),
				document.phone(),
				document.birthDate(),
				document.addresses(),
				document.preferences(),
				document.active(),
				now,
				now);
	}

}
