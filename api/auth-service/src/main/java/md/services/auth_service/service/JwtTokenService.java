package md.services.auth_service.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import md.services.auth_service.config.AuthSecurityProperties;
import md.services.auth_service.domain.AuthUserDocument;

@Service
public class JwtTokenService {

	private final AuthSecurityProperties properties;
	private final SecretKey key;

	public JwtTokenService(AuthSecurityProperties properties) {
		this.properties = properties;
		this.key = Keys.hmacShaKeyFor(properties.jwtSigningSecret().getBytes(StandardCharsets.UTF_8));
	}

	public String accessToken(AuthUserDocument user, List<String> roles, String userId) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(properties.accessTokenTtl());
		return Jwts.builder()
				.id(UUID.randomUUID().toString())
				.subject(user.email())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.claim("type", "access")
				.claim("authId", user.id())
				.claim("userId", userId)
				.claim("email", user.email())
				.claim("roles", roles)
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}

	public String refreshToken(AuthUserDocument user) {
		Instant now = Instant.now();
		return Jwts.builder()
				.id(UUID.randomUUID().toString())
				.subject(user.email())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(properties.refreshTokenTtl())))
				.claim("type", "refresh")
				.claim("authId", user.id())
				.claim("email", user.email())
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}

	public Claims verify(String token) {
		try {
			return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		}
		catch (JwtException exception) {
			throw new IllegalArgumentException("Token is invalid or expired.", exception);
		}
	}

	public long accessTokenExpiresInSeconds() {
		return properties.accessTokenTtl().toSeconds();
	}
}
