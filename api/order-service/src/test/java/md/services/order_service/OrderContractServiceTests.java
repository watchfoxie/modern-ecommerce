package md.services.order_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import md.services.order_service.api.CreateOrderRequest;
import md.services.order_service.api.CreateOrderRequest.DeliveryAddressRequest;
import md.services.order_service.api.CreateOrderRequest.PaymentRequest;
import md.services.order_service.api.OrderAcceptedResponse;
import md.services.order_service.client.CartInternalClient;
import md.services.order_service.client.CartInternalClient.CartDto;
import md.services.order_service.client.CartInternalClient.CartItemDto;
import md.services.order_service.client.CartInternalClient.ProductSnapshotDto;
import md.services.order_service.client.ProductInternalClient;
import md.services.order_service.client.ProductInternalClient.ProductInternalDto;
import md.services.order_service.domain.OrderDocument;
import md.services.order_service.repository.OrderRepository;
import md.services.order_service.service.OrderContractService;
import md.services.order_service.service.OrderEventCommand;
import md.services.order_service.service.OrderOutboxService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class OrderContractServiceTests {

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private CartInternalClient cartInternalClient;

	@Mock
	private ProductInternalClient productInternalClient;

	@Mock
	private OrderOutboxService orderOutboxService;

	@Captor
	private ArgumentCaptor<OrderDocument> orderCaptor;

	@Captor
	private ArgumentCaptor<OrderEventCommand> eventCaptor;

	@AfterEach
	void resetRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void createOrderPersistsValidatedCartAndPublishesEvent() {
		MockHttpServletRequest requestContext = new MockHttpServletRequest();
		requestContext.addHeader("X-User-Id", "user-1");
		requestContext.addHeader("X-User-Email", "customer@example.com");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requestContext));

		when(cartInternalClient.getCurrentCart("user-1")).thenReturn(new CartDto(
				"cart-1",
				"user-1",
				List.of(new CartItemDto(
						"prod-1",
						2,
						new BigDecimal("100.00"),
						new ProductSnapshotDto("Cart name", "cart-image", "phones"))),
				OffsetDateTime.now(),
				OffsetDateTime.now()));
		when(productInternalClient.getProduct("prod-1")).thenReturn(new ProductInternalDto(
				"prod-1",
				"Canonical name",
				"Brand",
				"phones",
				new BigDecimal("100.00"),
				null,
				"MDL",
				5,
				List.of("https://cdn.example/prod-1.png"),
				true));
		when(orderRepository.save(any(OrderDocument.class))).thenAnswer(invocation -> {
			OrderDocument order = invocation.getArgument(0);
			return new OrderDocument(
					"order-1",
					order.userId(),
					order.customerEmail(),
					order.orderNumber(),
					order.items(),
					order.deliveryAddress(),
					order.payment(),
					order.status(),
					order.totalAmount(),
					order.currency(),
					order.notes(),
					order.createdAt(),
					order.updatedAt());
		});

		OrderAcceptedResponse response = service().createOrder(request());

		verify(orderRepository).save(orderCaptor.capture());
		verify(orderOutboxService).enqueueAndDispatchOrderCreated(eventCaptor.capture());
		assertThat(response.status()).isEqualTo("ACCEPTED");
		assertThat(response.orderId()).isEqualTo("order-1");
		assertThat(orderCaptor.getValue().totalAmount()).isEqualByComparingTo("200.00");
		assertThat(orderCaptor.getValue().items().getFirst().name()).isEqualTo("Canonical name");
		assertThat(eventCaptor.getValue().totalAmount()).isEqualByComparingTo("200.00");
		assertThat(eventCaptor.getValue().currency()).isEqualTo("MDL");
	}

	private OrderContractService service() {
		return new OrderContractService(orderRepository, cartInternalClient, productInternalClient, orderOutboxService);
	}

	private CreateOrderRequest request() {
		return new CreateOrderRequest(
				new DeliveryAddressRequest("Main 1", "Chisinau", "Centru", "2001", "Customer", "+37360000000"),
				new PaymentRequest("CARD", "tx-1"),
				"leave at door");
	}
}
