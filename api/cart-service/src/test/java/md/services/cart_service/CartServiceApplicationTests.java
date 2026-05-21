package md.services.cart_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import md.services.cart_service.repository.CartRepository;

@SpringBootTest(properties = {
		"CART_MONGODB_URI=mongodb://localhost:27017/cart-service-test",
		"CART_SERVICE_DB_NAME=cart-service-test",
		"app.data.migrations.enabled=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
class CartServiceApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertThat(applicationContext.getBeansOfType(CartRepository.class)).isNotEmpty();
	}

}
