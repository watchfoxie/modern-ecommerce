package md.services.user_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import md.services.user_service.repository.UserProfileRepository;

@SpringBootTest(properties = {
		"USER_MONGODB_URI=mongodb://localhost:27017/user-service-test",
		"app.data.migrations.enabled=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
class UserServiceApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertThat(applicationContext.getBeansOfType(UserProfileRepository.class)).isNotEmpty();
	}

}
