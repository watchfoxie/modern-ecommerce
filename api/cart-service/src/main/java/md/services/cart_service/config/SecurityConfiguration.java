package md.services.cart_service.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@Profile("!local")
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Value("${app.security.internal-service-token:modern-ecommerce-local-internal-token}") String internalServiceToken)
			throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.addFilterBefore(new InternalHeaderAuthenticationFilter(internalServiceToken),
						UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
						.anyRequest().authenticated())
				.formLogin(withDefaults())
				.httpBasic(withDefaults())
				.build();
	}
}
