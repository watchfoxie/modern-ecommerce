package md.services.cart_service.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;

final class JwtDirectAuthenticationSupport {

	private JwtDirectAuthenticationSupport() {
	}

	static final class JwtAuthenticationFilter extends OncePerRequestFilter {

		private final SecretKey key;

		JwtAuthenticationFilter(String jwtSigningSecret) {
			this.key = Keys.hmacShaKeyFor(jwtSigningSecret.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
				throws ServletException, IOException {
			if (SecurityContextHolder.getContext().getAuthentication() == null) {
				String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
				if (authorization != null && authorization.startsWith("Bearer ")) {
					try {
						Claims claims = Jwts.parser().verifyWith(key).build()
								.parseSignedClaims(authorization.substring("Bearer ".length())).getPayload();
						if (!"access".equals(claims.get("type", String.class))) {
							response.sendError(HttpStatus.UNAUTHORIZED.value(), "Bearer token is invalid.");
							return;
						}
						String userId = firstNonBlank(claims.get("userId", String.class), claims.get("authId", String.class),
								claims.getSubject());
						if (!StringUtils.hasText(userId)) {
							response.sendError(HttpStatus.UNAUTHORIZED.value(), "Bearer token is missing subject identity.");
							return;
						}
						request.setAttribute("jwtClaims", claims);
						request.setAttribute("jwtUserId", claims.get("userId", String.class));
						request.setAttribute("jwtAuthId", claims.get("authId", String.class));
						request.setAttribute("jwtEmail", claims.get("email", String.class));
						SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId,
								"N/A", authorities(claims.get("roles"))));
						filterChain.doFilter(withIdentityHeaders(request, claims), response);
						return;
					} catch (JwtException | IllegalArgumentException exception) {
						response.sendError(HttpStatus.UNAUTHORIZED.value(), "Bearer token is invalid or expired.");
						return;
					}
				}
			}
			filterChain.doFilter(request, response);
		}

		private Collection<GrantedAuthority> authorities(Object rolesClaim) {
			return roles(rolesClaim).stream()
					.map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
					.map(SimpleGrantedAuthority::new)
					.map(GrantedAuthority.class::cast)
					.toList();
		}

		@SuppressWarnings("unchecked")
		private List<String> roles(Object rolesClaim) {
			if (rolesClaim instanceof List<?> list) {
				return list.stream().map(String::valueOf).filter(StringUtils::hasText).toList();
			}
			if (rolesClaim instanceof String roleString && StringUtils.hasText(roleString)) {
				return List.of(roleString.split(","));
			}
			return List.of();
		}

		private String firstNonBlank(String... values) {
			for (String value : values) {
				if (StringUtils.hasText(value)) {
					return value;
				}
			}
			return null;
		}

		private HttpServletRequest withIdentityHeaders(HttpServletRequest request, Claims claims) {
			Map<String, String> headers = new LinkedHashMap<>();
			putIfPresent(headers, "X-Auth-Id", claims.get("authId", String.class));
			putIfPresent(headers, "X-User-Id", claims.get("userId", String.class));
			putIfPresent(headers, "X-User-Email", claims.get("email", String.class));
			String roles = String.join(",", roles(claims.get("roles")));
			putIfPresent(headers, "X-Roles", roles);
			putIfPresent(headers, "X-User-Roles", roles);
			return new IdentityHeaderRequestWrapper(request, headers);
		}

		private void putIfPresent(Map<String, String> headers, String name, String value) {
			if (StringUtils.hasText(value)) {
				headers.put(name, value);
			}
		}
	}

	private static final class IdentityHeaderRequestWrapper extends HttpServletRequestWrapper {

		private final Map<String, String> extraHeaders;

		private IdentityHeaderRequestWrapper(HttpServletRequest request, Map<String, String> extraHeaders) {
			super(request);
			this.extraHeaders = Map.copyOf(extraHeaders);
		}

		@Override
		public String getHeader(String name) {
			String value = extraHeaders.get(name);
			return value != null ? value : super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			String value = extraHeaders.get(name);
			if (value == null) {
				return super.getHeaders(name);
			}
			return Collections.enumeration(List.of(value));
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			List<String> names = new ArrayList<>(extraHeaders.keySet());
			Enumeration<String> delegate = super.getHeaderNames();
			while (delegate.hasMoreElements()) {
				String name = delegate.nextElement();
				if (!extraHeaders.containsKey(name)) {
					names.add(name);
				}
			}
			return Collections.enumeration(names);
		}
	}
}
