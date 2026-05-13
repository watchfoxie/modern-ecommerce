package md.services.notification_service.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			InternalServiceTokenFilter internalServiceTokenFilter) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
				.logout(AbstractHttpConfigurer::disable)
				.rememberMe(AbstractHttpConfigurer::disable)
				.addFilterBefore(internalServiceTokenFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	InternalServiceTokenFilter internalServiceTokenFilter(
			@Value("${app.security.internal-service-token:modern-ecommerce-local-internal-token}") String token) {
		return new InternalServiceTokenFilter(token);
	}

	static final class InternalServiceTokenFilter extends OncePerRequestFilter {

		private final String token;

		InternalServiceTokenFilter(String token) {
			this.token = token;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
				throws ServletException, IOException {
			if (isPublic(request)) {
				filterChain.doFilter(request, response);
				return;
			}

			String providedToken = request.getHeader("X-Internal-Service-Token");
			if (token == null || token.isBlank() || !token.equals(providedToken)) {
				response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing or invalid internal service token.");
				return;
			}

			filterChain.doFilter(request, response);
		}

		private boolean isPublic(HttpServletRequest request) {
			String path = request.getRequestURI();
			return path.equals("/actuator/health")
					|| path.startsWith("/actuator/health/")
					|| path.equals("/actuator/info")
					|| path.equals("/v3/api-docs")
					|| path.startsWith("/v3/api-docs/")
					|| path.equals("/swagger-ui.html")
					|| path.startsWith("/swagger-ui/");
		}
	}

}
