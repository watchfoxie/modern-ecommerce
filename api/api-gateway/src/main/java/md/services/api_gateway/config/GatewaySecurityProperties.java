package md.services.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record GatewaySecurityProperties(
		String jwtSigningSecret,
		String internalServiceToken,
		int rateLimitPerMinute) {

	public GatewaySecurityProperties {
		if (jwtSigningSecret == null || jwtSigningSecret.isBlank()) {
			jwtSigningSecret = "modern-ecommerce-local-jwt-secret-change-me-32";
		}
		if (internalServiceToken == null || internalServiceToken.isBlank()) {
			internalServiceToken = "modern-ecommerce-local-internal-token";
		}
		if (rateLimitPerMinute <= 0) {
			rateLimitPerMinute = 120;
		}
	}
}
