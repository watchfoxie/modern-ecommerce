package md.services.product_service.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalActuatorSecurityConfiguration {

	@Bean
	@Order(1)
	SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
				.securityMatcher(
						"/actuator/health", "/actuator/health/**", "/actuator/info",
						"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
				.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain applicationSecurityFilterChain(
			HttpSecurity http,
			@Value("${app.security.internal-service-token}") String internalServiceToken) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.addFilterBefore(new GatewayHeaderAuthenticationFilter(internalServiceToken),
						UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/internal/products/*").permitAll()
						.requestMatchers(HttpMethod.GET, "/products", "/products/*", "/products/search",
								"/v1/products", "/v1/products/*", "/v1/products/search")
						.permitAll()
						.requestMatchers("/products", "/products/*", "/v1/products", "/v1/products/*")
						.hasRole("ADMIN")
						.anyRequest().authenticated())
				.formLogin(withDefaults())
				.httpBasic(withDefaults())
				.build();
	}

}
