package md.services.auth_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import md.services.auth_service.api.AuthIdentityDto;
import md.services.auth_service.api.AuthSignInRequest;
import md.services.auth_service.api.AuthSignUpRequest;
import md.services.auth_service.api.AuthTokenResponse;
import md.services.auth_service.api.PasswordResetConfirmRequest;
import md.services.auth_service.api.PasswordResetRequest;
import md.services.auth_service.api.TokenRefreshRequest;
import md.services.auth_service.client.UserProvisioningClient;
import md.services.auth_service.config.AuthSecurityProperties;
import md.services.auth_service.domain.AuthUserDocument;
import md.services.auth_service.domain.RoleDocument;
import md.services.auth_service.exception.ConflictException;
import md.services.auth_service.exception.UnauthorizedException;
import md.services.auth_service.repository.AuthUserRepository;
import md.services.auth_service.repository.RoleRepository;

@Service
public class AuthContractService {

	private static final String ACTIVE = "ACTIVE";
	private static final String ROLE_USER = "ROLE_USER";

	private final AuthUserRepository authUserRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;
	private final UserProvisioningClient userProvisioningClient;
	private final AuthSecurityProperties properties;

	public AuthContractService(AuthUserRepository authUserRepository, RoleRepository roleRepository,
			PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
			UserProvisioningClient userProvisioningClient, AuthSecurityProperties properties) {
		this.authUserRepository = authUserRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.userProvisioningClient = userProvisioningClient;
		this.properties = properties;
	}

	public AuthIdentityDto signUp(AuthSignUpRequest request) {
		String email = normalizeEmail(request.email());
		if (authUserRepository.existsByEmailIgnoreCase(email)) {
			throw new ConflictException("Email address is already registered.");
		}

		RoleDocument userRole = roleRepository.findByName(ROLE_USER)
				.orElseThrow(() -> new IllegalStateException("ROLE_USER is not provisioned."));
		Instant now = Instant.now();
		AuthUserDocument user = new AuthUserDocument(
				null,
				email,
				passwordEncoder.encode(request.password()),
				List.of(userRole.id()),
				ACTIVE,
				null,
				null,
				null,
				null,
				now,
				now,
				null);
		AuthUserDocument saved = authUserRepository.save(user);
		try {
			userProvisioningClient.createProfile(
					"auth-service",
					properties.internalServiceToken(),
					UserProvisioningClient.CreateUserProfileRequest.from(saved.id(), request));
		}
		catch (RuntimeException exception) {
			authUserRepository.deleteById(saved.id());
			throw new IllegalStateException("Could not provision matching user profile.", exception);
		}
		return toDto(saved);
	}

	public AuthTokenResponse signIn(AuthSignInRequest request) {
		AuthUserDocument user = authUserRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
				.orElseThrow(() -> new UnauthorizedException("Email or password is incorrect."));
		if (!ACTIVE.equals(user.status()) || !passwordEncoder.matches(request.password(), user.passwordHash())) {
			throw new UnauthorizedException("Email or password is incorrect.");
		}
		return issueTokens(touchLogin(user), null);
	}

	public void signOut(String authId) {
		if (authId == null || authId.isBlank()) {
			throw new UnauthorizedException("Missing authenticated identity.");
		}
		AuthUserDocument user = authUserRepository.findById(authId)
				.orElseThrow(() -> new UnauthorizedException("Authenticated identity was not found."));
		authUserRepository.save(withRefresh(user, null, null, Instant.now()));
	}

	public AuthTokenResponse refresh(TokenRefreshRequest request) {
		Claims claims;
		try {
			claims = jwtTokenService.verify(request.refreshToken());
		}
		catch (IllegalArgumentException exception) {
			throw new UnauthorizedException("Refresh token is invalid.");
		}
		if (!"refresh".equals(claims.get("type", String.class))) {
			throw new UnauthorizedException("Refresh token is invalid.");
		}
		String authId = claims.get("authId", String.class);
		AuthUserDocument user = authUserRepository.findById(authId)
				.orElseThrow(() -> new UnauthorizedException("Refresh token is invalid."));
		if (user.refreshTokenHash() == null || user.refreshTokenExpiry() == null
				|| user.refreshTokenExpiry().isBefore(Instant.now())
				|| !passwordEncoder.matches(tokenDigest(request.refreshToken()), user.refreshTokenHash())) {
			throw new UnauthorizedException("Refresh token is invalid.");
		}
		return issueTokens(user, null);
	}

	public void requestPasswordReset(PasswordResetRequest request) {
		authUserRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).ifPresent(user -> {
			String token = UUID.randomUUID().toString();
			authUserRepository.save(new AuthUserDocument(
					user.id(),
					user.email(),
					user.passwordHash(),
					user.roleIds(),
					user.status(),
					token,
					Instant.now().plus(properties.passwordResetTtl()),
					user.refreshTokenHash(),
					user.refreshTokenExpiry(),
					user.createdAt(),
					Instant.now(),
					user.lastLoginAt()));
		});
	}

	public void confirmPasswordReset(PasswordResetConfirmRequest request) {
		AuthUserDocument user = authUserRepository.findByPasswordResetToken(request.token())
				.orElseThrow(() -> new IllegalArgumentException("Password reset link is invalid or expired."));
		if (user.passwordResetExpiry() == null || user.passwordResetExpiry().isBefore(Instant.now())) {
			throw new IllegalArgumentException("Password reset link is invalid or expired.");
		}
		authUserRepository.save(new AuthUserDocument(
				user.id(),
				user.email(),
				passwordEncoder.encode(request.newPassword()),
				user.roleIds(),
				ACTIVE,
				null,
				null,
				null,
				null,
				user.createdAt(),
				Instant.now(),
				user.lastLoginAt()));
	}

	private AuthTokenResponse issueTokens(AuthUserDocument user, String userIdOverride) {
		List<String> roles = roleRepository.findByIdIn(user.roleIds()).stream().map(RoleDocument::name).toList();
		String userId = userIdOverride == null ? resolveUserProfileId(user.id()) : userIdOverride;
		String accessToken = jwtTokenService.accessToken(user, roles, userId);
		String refreshToken = jwtTokenService.refreshToken(user);
		authUserRepository.save(withRefresh(
				user,
				passwordEncoder.encode(tokenDigest(refreshToken)),
				Instant.now().plus(properties.refreshTokenTtl()),
				Instant.now()));
		return new AuthTokenResponse(accessToken, refreshToken, jwtTokenService.accessTokenExpiresInSeconds(), "Bearer");
	}

	private AuthUserDocument touchLogin(AuthUserDocument user) {
		AuthUserDocument updated = new AuthUserDocument(
				user.id(),
				user.email(),
				user.passwordHash(),
				user.roleIds(),
				user.status(),
				user.passwordResetToken(),
				user.passwordResetExpiry(),
				user.refreshTokenHash(),
				user.refreshTokenExpiry(),
				user.createdAt(),
				Instant.now(),
				Instant.now());
		return authUserRepository.save(updated);
	}

	private AuthUserDocument withRefresh(AuthUserDocument user, String refreshTokenHash, Instant refreshTokenExpiry,
			Instant updatedAt) {
		return new AuthUserDocument(
				user.id(),
				user.email(),
				user.passwordHash(),
				user.roleIds(),
				user.status(),
				user.passwordResetToken(),
				user.passwordResetExpiry(),
				refreshTokenHash,
				refreshTokenExpiry,
				user.createdAt(),
				updatedAt,
				user.lastLoginAt());
	}

	private AuthIdentityDto toDto(AuthUserDocument user) {
		return new AuthIdentityDto(user.id(), user.email(), user.status(), user.createdAt());
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase();
	}

	private String resolveUserProfileId(String authId) {
		return userProvisioningClient.findByAuthId("auth-service", properties.internalServiceToken(), authId).id();
	}

	private String tokenDigest(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 digest algorithm is unavailable.", exception);
		}
	}
}
