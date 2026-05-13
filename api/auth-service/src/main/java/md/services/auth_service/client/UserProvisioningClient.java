package md.services.auth_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import md.services.auth_service.api.AuthSignUpRequest;

@FeignClient(name = "user-service")
public interface UserProvisioningClient {

	@PostMapping("/users")
	UserProfileResponse createProfile(
			@RequestHeader("X-Internal-Service") String internalService,
			@RequestHeader("X-Internal-Service-Token") String internalServiceToken,
			@RequestBody CreateUserProfileRequest request);

	@GetMapping("/users/internal/by-auth/{authId}")
	UserProfileResponse findByAuthId(
			@RequestHeader("X-Internal-Service") String internalService,
			@RequestHeader("X-Internal-Service-Token") String internalServiceToken,
			@PathVariable String authId);

	record CreateUserProfileRequest(String authId, String email, String firstName, String lastName, String phone,
			String birthDate) {

		public static CreateUserProfileRequest from(String authId, AuthSignUpRequest request) {
			return new CreateUserProfileRequest(authId, request.email(), request.firstName(), request.lastName(), null, null);
		}
	}

	record UserProfileResponse(String id, String authId, String email, String firstName, String lastName) {
	}
}
