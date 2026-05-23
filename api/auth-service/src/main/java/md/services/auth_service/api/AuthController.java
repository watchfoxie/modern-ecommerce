package md.services.auth_service.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import md.services.auth_service.service.AuthContractService;

@Validated
@RestController
@RequestMapping({"", "/v1"})
@Tag(name = "Authentication")
public class AuthController {

	private final AuthContractService authContractService;

	public AuthController(AuthContractService authContractService) {
		this.authContractService = authContractService;
	}

	@PostMapping("/sign-up")
	@SecurityRequirements
	@Operation(summary = "Create a new user identity")
	public ResponseEntity<AuthIdentityDto> signUp(@Valid @RequestBody AuthSignUpRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authContractService.signUp(request));
	}

	@PostMapping("/sign-in")
	@SecurityRequirements
	@Operation(summary = "Authenticate and issue bearer tokens")
	public AuthTokenResponse signIn(@Valid @RequestBody AuthSignInRequest request) {
		return authContractService.signIn(request);
	}

	@PostMapping("/sign-out")
	@Operation(summary = "Invalidate the current authenticated session")
	public ResponseEntity<Void> signOut(@RequestHeader(value = "X-Auth-Id", required = false) String authId) {
		authContractService.signOut(authId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/token/refresh")
	@SecurityRequirements
	@Operation(summary = "Refresh bearer tokens")
	public AuthTokenResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
		return authContractService.refresh(request);
	}

	@PostMapping("/password-reset/request")
	@SecurityRequirements
	@Operation(summary = "Start password reset flow")
	public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
		authContractService.requestPasswordReset(request);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/password-reset/confirm")
	@SecurityRequirements
	@Operation(summary = "Confirm password reset")
	public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
		authContractService.confirmPasswordReset(request);
		return ResponseEntity.ok().build();
	}

}
