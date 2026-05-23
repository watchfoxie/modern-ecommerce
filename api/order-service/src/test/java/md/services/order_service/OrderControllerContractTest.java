package md.services.order_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import md.services.order_service.api.CreateOrderRequest;
import md.services.order_service.api.OrderAcceptedResponse;
import md.services.order_service.api.OrderCommandController;
import md.services.order_service.api.OrderDto;
import md.services.order_service.api.OrderDto.DeliveryAddressDto;
import md.services.order_service.api.OrderDto.OrderItemDto;
import md.services.order_service.api.OrderDto.PaymentDto;
import md.services.order_service.api.OrderStatusUpdateRequest;
import md.services.order_service.exception.ApiExceptionHandler;
import md.services.order_service.service.OrderContractService;

class OrderControllerContractTest {

	private OrderContractService orderContractService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		orderContractService = mock(OrderContractService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new OrderCommandController(orderContractService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void createOrderReturnsAcceptedResponse() throws Exception {
		when(orderContractService.createOrder(any(CreateOrderRequest.class)))
				.thenReturn(new OrderAcceptedResponse("ACCEPTED", "order-1", "ORD-20260501120000-1", "Order command accepted."));

		mockMvc.perform(post("/orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "deliveryAddress": {
								    "street": "Main 1",
								    "city": "Chisinau",
								    "district": "Centru",
								    "postalCode": "2001",
								    "recipientName": "Ana Popescu",
								    "recipientPhone": "+37360000000"
								  },
								  "payment": {
								    "method": "CARD",
								    "transactionId": "tx-1"
								  },
								  "notes": "leave at door"
								}
								"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.orderId").value("order-1"));
	}

	@Test
	void missingOrderReturnsNotFoundProblemDetail() throws Exception {
		when(orderContractService.getCurrentUserOrder("missing"))
				.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order was not found."));

		mockMvc.perform(get("/orders/missing"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void unsupportedStatusReturnsUnprocessableProblemDetail() throws Exception {
		when(orderContractService.updateStatus(any(String.class), any(OrderStatusUpdateRequest.class)))
				.thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Order status is not supported."));

		mockMvc.perform(patch("/orders/order-1/status")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "status": "BROKEN" }
								"""))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.title").value("Request cannot be processed"));
	}

	@Test
	void listOrderDetailsDoesNotExposeMongoDocumentFields() throws Exception {
		when(orderContractService.getCurrentUserOrder("order-1")).thenReturn(order());

		mockMvc.perform(get("/orders/order-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("order-1"))
				.andExpect(jsonPath("$.orderNumber").value("ORD-1"))
				.andExpect(jsonPath("$.class").doesNotExist());
	}

	private OrderDto order() {
		OffsetDateTime now = OffsetDateTime.parse("2026-05-01T12:00:00Z");
		return new OrderDto(
				"order-1",
				"user-1",
				"ORD-1",
				List.of(new OrderItemDto("prod-1", "Phone Pro", "Modern", "/phone.png", 1, new BigDecimal("1000.00"))),
				new DeliveryAddressDto("Main 1", "Chisinau", "Centru", "2001", "Ana Popescu", "+37360000000"),
				new PaymentDto("CARD", "PENDING", "tx-1"),
				"CREATED",
				new BigDecimal("1000.00"),
				"MDL",
				"leave at door",
				now,
				now);
	}
}
