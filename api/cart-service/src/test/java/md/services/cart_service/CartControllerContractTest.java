package md.services.cart_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import md.services.cart_service.api.AddCartItemRequest;
import md.services.cart_service.api.CartController;
import md.services.cart_service.api.CartDto;
import md.services.cart_service.api.CartItemDto;
import md.services.cart_service.api.ProductSnapshotDto;
import md.services.cart_service.exception.ApiExceptionHandler;
import md.services.cart_service.service.CartContractService;

class CartControllerContractTest {

	private CartContractService cartContractService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		cartContractService = mock(CartContractService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new CartController(cartContractService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void getCurrentCartReturnsCanonicalCartDto() throws Exception {
		when(cartContractService.getCurrentCart()).thenReturn(cart());

		mockMvc.perform(get("/carts/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value("user-1"))
				.andExpect(jsonPath("$.items[0].productId").value("prod-1"))
				.andExpect(jsonPath("$.items[0].priceAtAdd").value(1000.0));
	}

	@Test
	void addItemReturnsCreatedCart() throws Exception {
		when(cartContractService.addItem(any(AddCartItemRequest.class))).thenReturn(cart());

		mockMvc.perform(post("/carts/me/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productId": "prod-1",
								  "quantity": 1,
								  "priceAtAdd": 1000,
								  "productSnapshot": {
								    "name": "Phone Pro",
								    "imageUrl": "/phone.png",
								    "categorySlug": "smartphones"
								  }
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.items[0].productSnapshot.name").value("Phone Pro"));
	}

	@Test
	void invalidQuantityReturnsUnprocessableProblemDetail() throws Exception {
		mockMvc.perform(put("/carts/me/items/prod-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "quantity": 0 }
								"""))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.title").value("Request cannot be processed"));
	}

	@Test
	void missingAuthenticatedIdentityReturnsUnauthorizedProblemDetail() throws Exception {
		when(cartContractService.getCurrentCart())
				.thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user identity."));

		mockMvc.perform(get("/carts/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401));
	}

	private CartDto cart() {
		OffsetDateTime now = OffsetDateTime.parse("2026-05-01T12:00:00Z");
		return new CartDto("cart-1", "user-1", List.of(new CartItemDto(
				"prod-1",
				1,
				new BigDecimal("1000.00"),
				new ProductSnapshotDto("Phone Pro", "/phone.png", "smartphones"))), now, now);
	}
}
