package md.services.order_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
		properties = {
				"ORDER_MONGODB_URI=mongodb://localhost:27017/order-service-test",
				"app.data.migrations.enabled=false",
				"spring.rabbitmq.dynamic=false"
		}
)
class OrderServiceApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertThat(applicationContext.containsBean("inMemoryUserDetailsManager")).isFalse();
	}

}
