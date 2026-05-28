package md.services.order_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Value("${app.security.internal-service-token}") String internalServiceToken,
			@Value("${JWT_SIGNING_SECRET}") String jwtSigningSecret)
			throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.addFilterBefore(new JwtDirectAuthenticationSupport.JwtAuthenticationFilter(jwtSigningSecret),
						UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(new InternalHeaderAuthenticationFilter(internalServiceToken),
						UsernamePasswordAuthenticationFilter.class)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/actuator/health",
								"/actuator/health/**",
								"/actuator/info",
								"/v3/api-docs",
								"/v3/api-docs/**",
								"/swagger-ui.html",
								"/swagger-ui/**")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/orders/all", "/v1/orders/all")
						.hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/orders/*/status", "/v1/orders/*/status")
						.hasRole("ADMIN")
						.anyRequest().authenticated())
				.logout(AbstractHttpConfigurer::disable)
				.rememberMe(AbstractHttpConfigurer::disable)
				.build();
	}

}
