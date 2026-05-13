package md.services.api_gateway.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.SecretKey;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfiguration {

	@Bean
	SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.logout(ServerHttpSecurity.LogoutSpec::disable)
				.authorizeExchange(exchange -> exchange.anyExchange().permitAll())
				.build();
	}

	@Bean
	JwtGatewayVerifier jwtGatewayVerifier(GatewaySecurityProperties properties) {
		return new JwtGatewayVerifier(properties.jwtSigningSecret());
	}
}

class JwtGatewayVerifier {

	private final SecretKey key;

	JwtGatewayVerifier(String signingSecret) {
		this.key = Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8));
	}

	Claims verify(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class GatewaySecurityWebFilter implements WebFilter {

	private static final String CORRELATION_ID = "X-Correlation-Id";
	private static final Set<String> IDENTITY_HEADERS = Set.of("X-Auth-Id", "X-User-Id", "X-User-Email", "X-Roles",
			"X-User-Roles", "X-Internal-Service", "X-Internal-Service-Token");

	private final JwtGatewayVerifier verifier;
	private final GatewaySecurityProperties properties;
	private final Cache<String, AtomicInteger> rateCounters;

	GatewaySecurityWebFilter(JwtGatewayVerifier verifier, GatewaySecurityProperties properties) {
		this.verifier = verifier;
		this.properties = properties;
		this.rateCounters = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(20_000).build();
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getURI().getPath();
		String correlationId = correlationId(exchange);
		exchange.getResponse().getHeaders().set(CORRELATION_ID, correlationId);

		if (path.startsWith("/api/notification-service/internal/")) {
			exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
			return exchange.getResponse().setComplete();
		}
		if (path.equals("/api/user-service/users") || path.startsWith("/api/user-service/internal/")
				|| path.startsWith("/api/user-service/users/internal/")
				|| path.startsWith("/api/product-service/internal/")
				|| path.startsWith("/api/product-service/products/internal/")) {
			exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
			return exchange.getResponse().setComplete();
		}

		String rateKey = exchange.getRequest().getRemoteAddress() == null
				? "unknown"
				: exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
		if (rateCounters.get(rateKey, ignored -> new AtomicInteger()).incrementAndGet() > properties.rateLimitPerMinute()) {
			exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
			return exchange.getResponse().setComplete();
		}

		if (isPublic(exchange)) {
			return chain.filter(sanitize(exchange, correlationId, null));
		}

		String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
			return exchange.getResponse().setComplete();
		}

		try {
			Claims claims = verifier.verify(authorization.substring("Bearer ".length()));
			if (!"access".equals(claims.get("type", String.class))) {
				exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
				return exchange.getResponse().setComplete();
			}
			List<String> roles = roles(claims.get("roles"));
			if (!isAuthorized(exchange, roles)) {
				exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
				return exchange.getResponse().setComplete();
			}
			return chain.filter(sanitize(exchange, correlationId, claims));
		}
		catch (JwtException | IllegalArgumentException exception) {
			exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
			return exchange.getResponse().setComplete();
		}
	}

	private boolean isPublic(ServerWebExchange exchange) {
		String path = exchange.getRequest().getURI().getPath();
		HttpMethod method = exchange.getRequest().getMethod();
		return HttpMethod.OPTIONS.equals(method)
				|| path.equals("/actuator/health")
				|| path.startsWith("/actuator/health/")
				|| path.startsWith("/swagger-ui")
				|| path.startsWith("/v3/api-docs")
				|| path.endsWith("/v3/api-docs")
				|| (path.startsWith("/api/auth-service/")
						&& (path.endsWith("/sign-up") || path.endsWith("/sign-in") || path.contains("/password-reset/")
								|| path.endsWith("/token/refresh")))
				|| (HttpMethod.GET.equals(method) && (path.startsWith("/api/category-service/categories")
						|| path.startsWith("/api/product-service/products")));
	}

	private boolean isAuthorized(ServerWebExchange exchange, List<String> roles) {
		String path = exchange.getRequest().getURI().getPath();
		HttpMethod method = exchange.getRequest().getMethod();
		if ((path.startsWith("/api/category-service/categories") || path.startsWith("/api/product-service/products"))
				&& !HttpMethod.GET.equals(method)) {
			return roles.contains("ROLE_ADMIN");
		}
		if (path.equals("/api/order-service/orders/all") || path.matches("/api/order-service/orders/[^/]+/status")) {
			return roles.contains("ROLE_ADMIN");
		}
		return !roles.isEmpty();
	}

	private ServerWebExchange sanitize(ServerWebExchange exchange, String correlationId, Claims claims) {
		var builder = exchange.getRequest().mutate();
		builder.headers(headers -> IDENTITY_HEADERS.forEach(headers::remove));
		builder.header(CORRELATION_ID, correlationId);
		if (claims != null) {
			builder.header("X-Auth-Id", claims.get("authId", String.class));
			builder.header("X-User-Id", claims.get("userId", String.class));
			builder.header("X-User-Email", claims.get("email", String.class));
			builder.header("X-Roles", String.join(",", roles(claims.get("roles"))));
			builder.header("X-User-Roles", String.join(",", roles(claims.get("roles"))));
			builder.header("X-Internal-Service", "api-gateway");
			builder.header("X-Internal-Service-Token", properties.internalServiceToken());
		}
		return exchange.mutate().request(builder.build()).build();
	}

	private String correlationId(ServerWebExchange exchange) {
		String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID);
		return incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming;
	}

	@SuppressWarnings("unchecked")
	private List<String> roles(Object roles) {
		if (roles instanceof List<?> list) {
			return list.stream().map(String::valueOf).toList();
		}
		if (roles instanceof String roleString && !roleString.isBlank()) {
			return List.of(roleString.split(","));
		}
		return List.of();
	}
}
