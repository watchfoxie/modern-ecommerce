package md.services.notification_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
		properties = {
				"NOTIFICATION_MAIL_USERNAME=test@example.com",
				"NOTIFICATION_MAIL_PASSWORD=test-password",
				"spring.rabbitmq.dynamic=false",
				"spring.rabbitmq.listener.simple.auto-startup=false"
		}
)
class NotificationServiceApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertThat(applicationContext.containsBean("inMemoryUserDetailsManager")).isFalse();
	}

}
