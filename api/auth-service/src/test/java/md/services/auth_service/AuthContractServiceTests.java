package md.services.auth_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import md.services.auth_service.api.PasswordResetRequest;
import md.services.auth_service.client.NotificationClient;
import md.services.auth_service.client.UserProvisioningClient;
import md.services.auth_service.config.AuthSecurityProperties;
import md.services.auth_service.domain.AuthUserDocument;
import md.services.auth_service.repository.AuthUserRepository;
import md.services.auth_service.repository.RoleRepository;
import md.services.auth_service.service.AuthContractService;
import md.services.auth_service.service.JwtTokenService;

@ExtendWith(MockitoExtension.class)
class AuthContractServiceTests {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private UserProvisioningClient userProvisioningClient;

    @Mock
    private NotificationClient notificationClient;

    private AuthContractService authContractService;

    @BeforeEach
    void setUp() {
        authContractService = new AuthContractService(
                authUserRepository,
                roleRepository,
                passwordEncoder,
                jwtTokenService,
                userProvisioningClient,
                notificationClient,
                new AuthSecurityProperties(
                        "test-signing-secret",
                        "internal-token",
                        Duration.ofMinutes(30),
                        Duration.ofDays(7),
                        Duration.ofMinutes(45)),
                command -> command.run());
    }

    @Test
    void requestPasswordResetPersistsTokenAndDispatchesNotification() {
        when(authUserRepository.findByEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(existingUser()));
        when(authUserRepository.save(any(AuthUserDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, AuthUserDocument.class));

        authContractService.requestPasswordReset(new PasswordResetRequest("Customer@Example.com"));

        ArgumentCaptor<AuthUserDocument> savedUserCaptor = ArgumentCaptor.forClass(AuthUserDocument.class);
        verify(authUserRepository).save(savedUserCaptor.capture());
        AuthUserDocument savedUser = savedUserCaptor.getValue();
        assertThat(savedUser.passwordResetToken()).isNotBlank();
        assertThat(savedUser.passwordResetExpiry()).isAfter(Instant.now());

        ArgumentCaptor<NotificationClient.PasswordResetNotificationRequest> commandCaptor = ArgumentCaptor
                .forClass(NotificationClient.PasswordResetNotificationRequest.class);
        verify(notificationClient).sendPasswordReset(eq("auth-service"), eq("internal-token"), commandCaptor.capture());
        assertThat(commandCaptor.getValue().authId()).isEqualTo(savedUser.id());
        assertThat(commandCaptor.getValue().email()).isEqualTo(savedUser.email());
        assertThat(commandCaptor.getValue().token()).isEqualTo(savedUser.passwordResetToken());
        assertThat(commandCaptor.getValue().expiresAt()).isEqualTo(savedUser.passwordResetExpiry());
    }

    @Test
    void requestPasswordResetDoesNotEnumerateUnknownUsers() {
        when(authUserRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        authContractService.requestPasswordReset(new PasswordResetRequest("missing@example.com"));

        verify(authUserRepository, never()).save(any(AuthUserDocument.class));
        verifyNoInteractions(notificationClient);
    }

    @Test
    void requestPasswordResetKeepsSilentResponseWhenNotificationDispatchFails() {
        when(authUserRepository.findByEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(existingUser()));
        when(authUserRepository.save(any(AuthUserDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, AuthUserDocument.class));
        doThrow(new RuntimeException("notification-service unavailable"))
                .when(notificationClient)
                .sendPasswordReset(eq("auth-service"), eq("internal-token"),
                        any(NotificationClient.PasswordResetNotificationRequest.class));

        assertThatCode(() -> authContractService.requestPasswordReset(new PasswordResetRequest("customer@example.com")))
                .doesNotThrowAnyException();

        verify(authUserRepository).save(any(AuthUserDocument.class));
        verify(notificationClient).sendPasswordReset(eq("auth-service"), eq("internal-token"),
                any(NotificationClient.PasswordResetNotificationRequest.class));
    }

    private AuthUserDocument existingUser() {
        return new AuthUserDocument(
                "auth-1",
                "customer@example.com",
                "encoded-password",
                List.of("role-user"),
                "ACTIVE",
                null,
                null,
                null,
                null,
                Instant.parse("2026-06-04T06:00:00Z"),
                Instant.parse("2026-06-04T06:00:00Z"),
                Instant.parse("2026-06-04T06:00:00Z"));
    }
}