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
			jwtSigningSecret = "modern-ecommerce-local-jwt-secret-change-me-32";
		}
		if (internalServiceToken == null || internalServiceToken.isBlank()) {
			internalServiceToken = "modern-ecommerce-local-internal-token";
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
