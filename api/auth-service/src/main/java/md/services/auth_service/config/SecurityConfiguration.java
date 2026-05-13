package md.services.auth_service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthSecurityProperties.class)
public class SecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
		return new PasswordEncoder() {
			@Override
			public String encode(CharSequence rawPassword) {
				return bcrypt.encode(rawPassword);
			}

			@Override
			public boolean matches(CharSequence rawPassword, String encodedPassword) {
				if (encodedPassword != null && encodedPassword.startsWith("$2")) {
					return bcrypt.matches(rawPassword, encodedPassword);
				}
				return encodedPassword != null && encodedPassword.contentEquals(rawPassword);
			}
		};
	}
}
