package md.services.user_service.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import md.services.user_service.service.UserContractService;

@Validated
@RestController
@RequestMapping("/users")
@Tag(name = "User profiles")
public class UserProfileController {

	private final UserContractService userContractService;

	public UserProfileController(UserContractService userContractService) {
		this.userContractService = userContractService;
	}

	@GetMapping("/me")
	@Operation(summary = "Get the authenticated user's profile")
	public UserProfileDto getCurrentUserProfile(@RequestHeader("X-Auth-Id") String authId) {
		return userContractService.getCurrentUserProfile(authId);
	}

	@PutMapping("/me")
	@Operation(summary = "Update the authenticated user's profile")
	public UserProfileDto updateCurrentUserProfile(@RequestHeader("X-Auth-Id") String authId,
			@Valid @RequestBody UserProfileUpdateRequest request) {
		return userContractService.updateCurrentUserProfile(authId, request);
	}

	@PostMapping("/me/addresses")
	@Operation(summary = "Add a delivery address to the authenticated user's profile")
	public ResponseEntity<UserProfileDto> addAddress(@RequestHeader("X-Auth-Id") String authId,
			@Valid @RequestBody AddressRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userContractService.addAddress(authId, request));
	}

	@PutMapping("/me/addresses/{addressIndex}")
	@Operation(summary = "Replace a delivery address by index")
	public UserProfileDto replaceAddress(@RequestHeader("X-Auth-Id") String authId,
			@PathVariable @PositiveOrZero int addressIndex,
			@Valid @RequestBody AddressRequest request) {
		return userContractService.replaceAddress(authId, addressIndex, request);
	}

	@DeleteMapping("/me/addresses/{addressIndex}")
	@Operation(summary = "Delete a delivery address by index")
	public ResponseEntity<Void> deleteAddress(@RequestHeader("X-Auth-Id") String authId,
			@PathVariable @PositiveOrZero int addressIndex) {
		userContractService.deleteAddress(authId, addressIndex);
		return ResponseEntity.noContent().build();
	}

	@PostMapping
	@Hidden
	@Operation(summary = "Create a user profile from an internal auth-service provisioning request")
	public ResponseEntity<UserProfileDto> createInternalProfile(@Valid @RequestBody CreateUserProfileRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userContractService.createInternalProfile(request));
	}

	@GetMapping("/internal/by-auth/{authId}")
	@Hidden
	@Operation(summary = "Resolve a user profile by auth identity for internal services")
	public UserProfileDto findInternalByAuthId(@PathVariable String authId) {
		return userContractService.findByAuthId(authId);
	}

}
