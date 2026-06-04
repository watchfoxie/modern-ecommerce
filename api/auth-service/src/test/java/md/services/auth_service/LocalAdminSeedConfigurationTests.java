package md.services.auth_service.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import md.services.auth_service.client.UserProvisioningClient;
import md.services.auth_service.domain.AuthUserDocument;
import md.services.auth_service.domain.RoleDocument;
import md.services.auth_service.repository.AuthUserRepository;
import md.services.auth_service.repository.RoleRepository;

@ExtendWith(MockitoExtension.class)
class LocalAdminSeedConfigurationTests {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserProvisioningClient userProvisioningClient;

    private LocalAdminSeedConfiguration configuration;
    private AuthSecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        configuration = new LocalAdminSeedConfiguration();
        securityProperties = new AuthSecurityProperties(
                "test-signing-secret",
                "internal-token",
                Duration.ofMinutes(30),
                Duration.ofDays(7),
                Duration.ofMinutes(45));
    }

    @Test
    void localAdminSeedRunnerRefreshesPasswordForExistingAdmin() throws Exception {
        RoleDocument userRole = new RoleDocument("role-user", "ROLE_USER", "User role",
                Instant.parse("2026-06-04T06:00:00Z"));
        RoleDocument adminRole = new RoleDocument("role-admin", "ROLE_ADMIN", "Admin role",
                Instant.parse("2026-06-04T06:00:00Z"));
        AuthUserDocument existingAdmin = new AuthUserDocument(
                "auth-admin",
                "admin@modern-ecommerce.local",
                "old-hash",
                List.of("role-user", "role-admin"),
                "ACTIVE",
                null,
                null,
                null,
                null,
                Instant.parse("2026-06-04T06:00:00Z"),
                Instant.parse("2026-06-04T06:00:00Z"),
                null);

        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(authUserRepository.findByEmailIgnoreCase("admin@modern-ecommerce.local"))
                .thenReturn(Optional.of(existingAdmin));
        when(passwordEncoder.matches("Admin123!", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("Admin123!")).thenReturn("new-hash");
        when(authUserRepository.save(any(AuthUserDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, AuthUserDocument.class));
        when(userProvisioningClient.findByAuthId("auth-service", "internal-token", "auth-admin"))
                .thenReturn(new UserProvisioningClient.UserProfileResponse(
                        "user-admin",
                        "auth-admin",
                        "admin@modern-ecommerce.local",
                        "Platform",
                        "Administrator"));

        ApplicationRunner runner = configuration.localAdminSeedRunner(
                true,
                "admin@modern-ecommerce.local",
                "Admin123!",
                "Platform",
                "Administrator",
                authUserRepository,
                roleRepository,
                passwordEncoder,
                userProvisioningClient,
                securityProperties);

        runner.run(null);

        verify(passwordEncoder).matches("Admin123!", "old-hash");
        verify(passwordEncoder).encode("Admin123!");
        verify(authUserRepository).save(any(AuthUserDocument.class));
    }

    @Test
    void localAdminSeedRunnerKeepsExistingAdminWhenPasswordAlreadyMatches() throws Exception {
        RoleDocument userRole = new RoleDocument("role-user", "ROLE_USER", "User role",
                Instant.parse("2026-06-04T06:00:00Z"));
        RoleDocument adminRole = new RoleDocument("role-admin", "ROLE_ADMIN", "Admin role",
                Instant.parse("2026-06-04T06:00:00Z"));
        AuthUserDocument existingAdmin = new AuthUserDocument(
                "auth-admin",
                "admin@modern-ecommerce.local",
                "current-hash",
                List.of("role-user", "role-admin"),
                "ACTIVE",
                null,
                null,
                null,
                null,
                Instant.parse("2026-06-04T06:00:00Z"),
                Instant.parse("2026-06-04T06:00:00Z"),
                null);

        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(authUserRepository.findByEmailIgnoreCase("admin@modern-ecommerce.local"))
                .thenReturn(Optional.of(existingAdmin));
        when(passwordEncoder.matches("Admin123!", "current-hash")).thenReturn(true);
        when(userProvisioningClient.findByAuthId("auth-service", "internal-token", "auth-admin"))
                .thenReturn(new UserProvisioningClient.UserProfileResponse(
                        "user-admin",
                        "auth-admin",
                        "admin@modern-ecommerce.local",
                        "Platform",
                        "Administrator"));

        ApplicationRunner runner = configuration.localAdminSeedRunner(
                true,
                "admin@modern-ecommerce.local",
                "Admin123!",
                "Platform",
                "Administrator",
                authUserRepository,
                roleRepository,
                passwordEncoder,
                userProvisioningClient,
                securityProperties);

        runner.run(null);

        verify(passwordEncoder).matches("Admin123!", "current-hash");
        verify(passwordEncoder, never()).encode("Admin123!");
        verify(authUserRepository, never()).save(any(AuthUserDocument.class));
    }
}
