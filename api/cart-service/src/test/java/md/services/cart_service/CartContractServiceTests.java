package md.services.cart_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import md.services.cart_service.api.AddCartItemRequest;
import md.services.cart_service.api.CartDto;
import md.services.cart_service.api.ProductSnapshotDto;
import md.services.cart_service.client.ProductInternalClient;
import md.services.cart_service.client.ProductInternalClient.ProductInternalDto;
import md.services.cart_service.domain.CartDocument;
import md.services.cart_service.repository.CartRepository;
import md.services.cart_service.service.CartContractService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class CartContractServiceTests {

	@Mock
	private CartRepository cartRepository;

	@Mock
	private ProductInternalClient productInternalClient;

	@AfterEach
	void resetRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void addItemStoresCanonicalProductSnapshotAndPrice() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-User-Id", "user-1");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		when(productInternalClient.getProduct("prod-1")).thenReturn(new ProductInternalDto(
				"prod-1",
				"Canonical name",
				"Brand",
				"phones",
				new BigDecimal("100.00"),
				new BigDecimal("89.99"),
				"MDL",
				10,
				List.of("https://cdn.example/prod-1.png"),
				true));
		when(cartRepository.findByUserId("user-1")).thenReturn(Optional.empty());
		when(cartRepository.save(any(CartDocument.class))).thenAnswer(invocation -> {
			CartDocument cart = invocation.getArgument(0);
			return new CartDocument(
					cart.id() == null ? "cart-1" : cart.id(),
					cart.userId(),
					cart.items(),
					cart.createdAt(),
					cart.updatedAt());
		});

		CartDto cart = new CartContractService(cartRepository, productInternalClient).addItem(new AddCartItemRequest(
				"prod-1",
				2,
				new BigDecimal("1.00"),
				new ProductSnapshotDto("Client name", "client-image", "client-category")));

		assertThat(cart.id()).isEqualTo("cart-1");
		assertThat(cart.items()).hasSize(1);
		assertThat(cart.items().getFirst().priceAtAdd()).isEqualByComparingTo("89.99");
		assertThat(cart.items().getFirst().productSnapshot().name()).isEqualTo("Canonical name");
		assertThat(cart.items().getFirst().productSnapshot().imageUrl()).isEqualTo("https://cdn.example/prod-1.png");
		assertThat(cart.items().getFirst().productSnapshot().categorySlug()).isEqualTo("phones");
	}
}
