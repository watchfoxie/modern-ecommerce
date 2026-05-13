package md.services.order_service.config;

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

public class InternalHeaderAuthenticationFilter extends OncePerRequestFilter {

	private final String internalServiceToken;

	public InternalHeaderAuthenticationFilter(String internalServiceToken) {
		this.internalServiceToken = internalServiceToken;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null
				&& internalServiceToken.equals(request.getHeader("X-Internal-Service-Token"))) {
			String userId = request.getHeader("X-User-Id");
			if (StringUtils.hasText(userId)) {
				SecurityContextHolder.getContext()
						.setAuthentication(new UsernamePasswordAuthenticationToken(userId, "N/A", authorities(request)));
			}
		}
		filterChain.doFilter(request, response);
	}

	private Collection<GrantedAuthority> authorities(HttpServletRequest request) {
		String roles = request.getHeader("X-User-Roles");
		if (!StringUtils.hasText(roles)) {
			roles = request.getHeader("X-Roles");
		}
		if (!StringUtils.hasText(roles)) {
			roles = "ROLE_USER";
		}
		return Arrays.stream(roles.split(","))
				.map(String::trim)
				.filter(StringUtils::hasText)
				.map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
				.map(SimpleGrantedAuthority::new)
				.map(GrantedAuthority.class::cast)
				.toList();
	}
}
