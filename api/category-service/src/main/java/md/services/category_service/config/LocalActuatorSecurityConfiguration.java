package md.services.category_service.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
	SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.addFilterBefore(new GatewayHeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/categories", "/categories/*").permitAll()
						.requestMatchers("/categories", "/categories/*").hasRole("ADMIN")
						.anyRequest().authenticated())
				.formLogin(withDefaults())
				.httpBasic(withDefaults())
				.build();
	}

}
