package md.services.auth_service.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record AuthSecurityProperties(
		String jwtSigningSecret,
		String internalServiceToken,
		Duration accessTokenTtl,
		Duration refreshTokenTtl,
		Duration passwordResetTtl) {

	public AuthSecurityProperties {
		if (jwtSigningSecret == null || jwtSigningSecret.isBlank()) {
			throw new IllegalArgumentException("app.security.jwt-signing-secret must be configured.");
		}
		if (internalServiceToken == null || internalServiceToken.isBlank()) {
			throw new IllegalArgumentException("app.security.internal-service-token must be configured.");
		}
		if (accessTokenTtl == null) {
			accessTokenTtl = Duration.ofMinutes(30);
		}
		if (refreshTokenTtl == null) {
			refreshTokenTtl = Duration.ofDays(7);
		}
		if (passwordResetTtl == null) {
			passwordResetTtl = Duration.ofMinutes(30);
		}
	}
}
