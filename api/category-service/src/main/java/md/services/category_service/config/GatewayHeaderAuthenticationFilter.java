package md.services.category_service.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String USER_ROLES_HEADER = "X-User-Roles";
	private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

	private final String internalServiceToken;

	public GatewayHeaderAuthenticationFilter(String internalServiceToken) {
		this.internalServiceToken = internalServiceToken;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			String userId = request.getHeader(USER_ID_HEADER);
			String roles = request.getHeader(USER_ROLES_HEADER);
			String token = request.getHeader(INTERNAL_SERVICE_TOKEN_HEADER);
			if (internalServiceToken.equals(token) && StringUtils.hasText(userId) && StringUtils.hasText(roles)) {
				Collection<GrantedAuthority> authorities = Arrays.stream(roles.split(","))
						.map(String::trim)
						.filter(StringUtils::hasText)
						.map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
						.map(SimpleGrantedAuthority::new)
						.map(GrantedAuthority.class::cast)
						.toList();
				SecurityContextHolder.getContext()
						.setAuthentication(new UsernamePasswordAuthenticationToken(userId, "N/A", authorities));
			}
		}
		filterChain.doFilter(request, response);
	}

}
