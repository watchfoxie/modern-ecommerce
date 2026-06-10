package md.services.product_service.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@Profile("!local")
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			@Value("${app.security.internal-service-token}") String internalServiceToken) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.addFilterBefore(new GatewayHeaderAuthenticationFilter(internalServiceToken),
						UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/internal/products/*").permitAll()
						.requestMatchers(HttpMethod.POST, "/internal/products/*/stock/decrement").permitAll()
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
