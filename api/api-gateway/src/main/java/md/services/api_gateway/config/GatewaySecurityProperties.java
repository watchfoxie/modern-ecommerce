package md.services.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record GatewaySecurityProperties(
		String jwtSigningSecret,
		String internalServiceToken,
		int rateLimitPerMinute) {

	public GatewaySecurityProperties {
		if (jwtSigningSecret == null || jwtSigningSecret.isBlank()) {
			throw new IllegalArgumentException("app.security.jwt-signing-secret must be configured.");
		}
		if (internalServiceToken == null || internalServiceToken.isBlank()) {
			throw new IllegalArgumentException("app.security.internal-service-token must be configured.");
		}
		if (rateLimitPerMinute <= 0) {
			rateLimitPerMinute = 120;
		}
	}
}
