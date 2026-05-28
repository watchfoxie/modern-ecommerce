package md.services.api_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

class GatewaySecurityWebFilterTest {

	private static final String SECRET = "modern-ecommerce-test-jwt-signing-secret-minimum-32-characters";
	private static final String INTERNAL_TOKEN = "modern-ecommerce-local-internal-token";

	@Test
	void rejectsProtectedRouteWithoutBearerToken() {
		MockServerWebExchange exchange = exchange("/api/cart-service/carts/me", null);

		filter(120).filter(exchange, successChain()).block();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsExpiredBearerToken() {
		MockServerWebExchange exchange = exchange("/api/cart-service/carts/me", expiredToken());

		filter(120).filter(exchange, successChain()).block();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void propagatesVerifiedIdentityAndRemovesSpoofedHeaders() {
		AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
		MockServerWebExchange exchange = exchange("/api/cart-service/carts/me", userToken("ROLE_USER"), true);

		filter(120).filter(exchange, capturingChain(captured)).block();

		HttpHeaders headers = captured.get().getRequest().getHeaders();
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(headers.getFirst("X-Auth-Id")).isEqualTo("auth-1");
		assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-1");
		assertThat(headers.getFirst("X-User-Email")).isEqualTo("customer@example.com");
		assertThat(headers.getFirst("X-Roles")).isEqualTo("ROLE_USER");
		assertThat(headers.getFirst("X-Internal-Service-Token")).isEqualTo(INTERNAL_TOKEN);
	}

	@Test
	void rejectsUserRoleOnAdminRoutes() {
		MockServerWebExchange exchange = exchange("/api/order-service/orders/order-1/status", userToken("ROLE_USER"));

		filter(120).filter(exchange, successChain()).block();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void rejectsUserRoleOnVersionedAdminRoutes() {
		List<String> paths = List.of(
				"/api/order-service/v1/orders/all",
				"/api/order-service/v1/orders/order-1/status");

		for (String path : paths) {
			MockServerWebExchange exchange = exchange(path, userToken("ROLE_USER"));

			filter(120).filter(exchange, successChain()).block();

			assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		}
	}

	@Test
	void permitsVersionedPublicCatalogReadsWithoutBearerToken() {
		List<String> paths = List.of(
				"/api/category-service/v1/categories",
				"/api/product-service/v1/products",
				"/api/product-service/v1/products/search");

		for (String path : paths) {
			MockServerWebExchange exchange = exchange(path, null);

			filter(120).filter(exchange, successChain()).block();

			assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
		}
	}

	@Test
	void permitsProxiedSwaggerUiAndApiDocsWithoutBearerToken() {
		List<String> paths = List.of(
				"/api/auth-service/swagger-ui.html",
				"/api/notification-service/swagger-ui/index.html",
				"/api/order-service/v3/api-docs",
				"/api/cart-service/v3/api-docs/swagger-config");

		for (String path : paths) {
			MockServerWebExchange exchange = exchange(path, null);

			filter(120).filter(exchange, successChain()).block();

			assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
		}
	}

	@Test
	void hidesInternalRoutesFromPublicGatewaySurface() {
		List<String> paths = List.of(
				"/api/notification-service/internal/notifications",
				"/api/notification-service/v1/internal/notifications",
				"/api/product-service/internal/products/prod-1",
				"/api/product-service/v1/products/internal/prod-1",
				"/api/user-service/users/internal/by-auth/auth-1",
				"/api/user-service/v1/users/internal/by-auth/auth-1");

		for (String path : paths) {
			MockServerWebExchange exchange = exchange(path, userToken("ROLE_ADMIN"));

			filter(120).filter(exchange, successChain()).block();

			assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	@Test
	void appliesPerMinuteRateLimitBeforeRouting() {
		GatewaySecurityWebFilter filter = filter(1);
		MockServerWebExchange first = exchange("/api/product-service/products", null);
		MockServerWebExchange second = exchange("/api/product-service/products", null);

		filter.filter(first, successChain()).block();
		filter.filter(second, successChain()).block();

		assertThat(first.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	private GatewaySecurityWebFilter filter(int rateLimit) {
		GatewaySecurityProperties properties = new GatewaySecurityProperties(SECRET, INTERNAL_TOKEN, rateLimit);
		return new GatewaySecurityWebFilter(new JwtGatewayVerifier(SECRET), properties);
	}

	private WebFilterChain successChain() {
		return exchange -> {
			exchange.getResponse().setStatusCode(HttpStatus.OK);
			return exchange.getResponse().setComplete();
		};
	}

	private WebFilterChain capturingChain(AtomicReference<ServerWebExchange> captured) {
		return exchange -> {
			captured.set(exchange);
			exchange.getResponse().setStatusCode(HttpStatus.OK);
			return exchange.getResponse().setComplete();
		};
	}

	private MockServerWebExchange exchange(String path, String token) {
		return exchange(path, token, false);
	}

	private MockServerWebExchange exchange(String path, String token, boolean spoofIdentityHeaders) {
		MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path)
				.remoteAddress(new InetSocketAddress("127.0.0.1", 54000));
		if (token != null) {
			builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (spoofIdentityHeaders) {
			builder.header("X-User-Id", "spoofed");
			builder.header("X-Internal-Service-Token", "spoofed");
		}
		return MockServerWebExchange.from(builder.build());
	}

	private String userToken(String role) {
		return token(Instant.now().plusSeconds(600), role);
	}

	private String expiredToken() {
		return token(Instant.now().minusSeconds(60), "ROLE_USER");
	}

	private String token(Instant expiresAt, String role) {
		SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
		return Jwts.builder()
				.subject("auth-1")
				.claim("type", "access")
				.claim("authId", "auth-1")
				.claim("userId", "user-1")
				.claim("email", "customer@example.com")
				.claim("roles", List.of(role))
				.expiration(Date.from(expiresAt))
				.signWith(key)
				.compact();
	}
}
