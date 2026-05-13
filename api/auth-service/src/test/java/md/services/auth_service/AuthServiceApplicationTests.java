package md.services.auth_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import md.services.auth_service.repository.AuthUserRepository;
import md.services.auth_service.repository.RoleRepository;

@SpringBootTest(properties = {
		"AUTH_MONGODB_URI=mongodb://localhost:27017/auth-service-test",
		"app.data.migrations.enabled=false",
		"app.startup.mongo-verification.enabled=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
class AuthServiceApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertThat(applicationContext.getBeansOfType(AuthUserRepository.class)).isNotEmpty();
		assertThat(applicationContext.getBeansOfType(RoleRepository.class)).isNotEmpty();
	}

}
