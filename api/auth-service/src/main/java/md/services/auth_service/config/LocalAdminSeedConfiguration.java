package md.services.auth_service.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import md.services.auth_service.client.UserProvisioningClient;
import md.services.auth_service.domain.AuthUserDocument;
import md.services.auth_service.domain.RoleDocument;
import md.services.auth_service.repository.AuthUserRepository;
import md.services.auth_service.repository.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalAdminSeedConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(LocalAdminSeedConfiguration.class);
	private static final String ACTIVE = "ACTIVE";
	private static final String ROLE_USER = "ROLE_USER";
	private static final String ROLE_ADMIN = "ROLE_ADMIN";
	private static final long PROFILE_PROVISIONING_TIMEOUT_MILLIS = 60_000;
	private static final long PROFILE_PROVISIONING_RETRY_DELAY_MILLIS = 5_000;

	@Bean
	ApplicationRunner localAdminSeedRunner(
			@Value("${app.data.seed.enabled:false}") boolean seedEnabled,
			@Value("${app.seed.admin.email:admin@modern-ecommerce.local}") String email,
			@Value("${app.seed.admin.password:Admin123!}") String password,
			@Value("${app.seed.admin.first-name:Platform}") String firstName,
			@Value("${app.seed.admin.last-name:Administrator}") String lastName,
			AuthUserRepository authUserRepository,
			RoleRepository roleRepository,
			PasswordEncoder passwordEncoder,
			UserProvisioningClient userProvisioningClient,
			AuthSecurityProperties properties) {
		return ignored -> {
			if (!seedEnabled) {
				return;
			}

			RoleDocument userRole = roleRepository.findByName(ROLE_USER)
					.orElseThrow(() -> new IllegalStateException("ROLE_USER is not provisioned."));
			RoleDocument adminRole = roleRepository.findByName(ROLE_ADMIN)
					.orElseThrow(() -> new IllegalStateException("ROLE_ADMIN is not provisioned."));

			String normalizedEmail = email.trim().toLowerCase();
			AuthUserDocument admin = authUserRepository.findByEmailIgnoreCase(normalizedEmail)
					.map(existing -> ensureAdminRoles(existing, userRole.id(), adminRole.id(), authUserRepository))
					.orElseGet(() -> createAdmin(normalizedEmail, password, userRole.id(), adminRole.id(), authUserRepository,
							passwordEncoder));

			ensureAdminProfile(admin, normalizedEmail, firstName, lastName, userProvisioningClient, properties);
			logger.info("[AUTH-SERVICE] Local admin identity is ready for {}", normalizedEmail);
		};
	}

	private AuthUserDocument ensureAdminRoles(AuthUserDocument existing, String userRoleId, String adminRoleId,
			AuthUserRepository authUserRepository) {
		Set<String> roleIds = new LinkedHashSet<>(existing.roleIds() == null ? List.of() : existing.roleIds());
		boolean changed = roleIds.add(userRoleId) | roleIds.add(adminRoleId);
		if (!changed) {
			return existing;
		}
		AuthUserDocument updated = new AuthUserDocument(
				existing.id(),
				existing.email(),
				existing.passwordHash(),
				List.copyOf(roleIds),
				ACTIVE,
				existing.passwordResetToken(),
				existing.passwordResetExpiry(),
				existing.refreshTokenHash(),
				existing.refreshTokenExpiry(),
				existing.createdAt(),
				Instant.now(),
				existing.lastLoginAt());
		return authUserRepository.save(updated);
	}

	private AuthUserDocument createAdmin(String email, String password, String userRoleId, String adminRoleId,
			AuthUserRepository authUserRepository, PasswordEncoder passwordEncoder) {
		Instant now = Instant.now();
		return authUserRepository.save(new AuthUserDocument(
				null,
				email,
				passwordEncoder.encode(password),
				List.of(userRoleId, adminRoleId),
				ACTIVE,
				null,
				null,
				null,
				null,
				now,
				now,
				null));
	}

	private void ensureAdminProfile(AuthUserDocument admin, String email, String firstName, String lastName,
			UserProvisioningClient userProvisioningClient, AuthSecurityProperties properties) {
		long deadline = System.currentTimeMillis() + PROFILE_PROVISIONING_TIMEOUT_MILLIS;
		RuntimeException lastException = null;

		while (System.currentTimeMillis() < deadline) {
			try {
				userProvisioningClient.findByAuthId("auth-service", properties.internalServiceToken(), admin.id());
				return;
			} catch (RuntimeException exception) {
				lastException = exception;
			}

			try {
				userProvisioningClient.createProfile(
						"auth-service",
						properties.internalServiceToken(),
						new UserProvisioningClient.CreateUserProfileRequest(
								admin.id(),
								email,
								defaultIfBlank(firstName, "Platform"),
								defaultIfBlank(lastName, "Administrator"),
								null,
								null));
				return;
			} catch (RuntimeException exception) {
				lastException = exception;
			}

			try {
				Thread.sleep(PROFILE_PROVISIONING_RETRY_DELAY_MILLIS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		logger.warn(
				"[AUTH-SERVICE] Skipping local admin profile provisioning after retry timeout because user-service is not ready yet: {}",
				lastException == null ? "unknown error" : lastException.getMessage());
	}

	private String defaultIfBlank(String value, String fallback) {
		return StringUtils.hasText(value) ? value.trim() : fallback;
	}
}
