package md.services.product_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import md.services.product_service.api.PagedResponseDto;
import md.services.product_service.api.ProductController;
import md.services.product_service.api.ProductDto;
import md.services.product_service.api.ProductUpsertRequest;
import md.services.product_service.exception.ApiExceptionHandler;
import md.services.product_service.exception.DuplicateResourceException;
import md.services.product_service.exception.ResourceNotFoundException;
import md.services.product_service.service.ProductContractService;

class ProductControllerContractTest {

	private ProductContractService productContractService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		productContractService = mock(ProductContractService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new ProductController(productContractService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void listProductsReturnsPagedEnvelope() throws Exception {
		when(productContractService.listProducts("smartphones", true, 0, 12, "createdAt", "desc"))
				.thenReturn(new PagedResponseDto<>(List.of(product()), 0, 12, 1, 1, true, true));

		mockMvc.perform(get("/products")
						.param("categorySlug", "smartphones")
						.param("hasPromotion", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].slug").value("phone-pro"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void missingProductReturnsNotFoundProblemDetail() throws Exception {
		when(productContractService.getProduct("missing")).thenThrow(new ResourceNotFoundException("Product was not found."));

		mockMvc.perform(get("/products/missing"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void duplicateProductSlugReturnsConflictProblemDetail() throws Exception {
		when(productContractService.createProduct(any(ProductUpsertRequest.class)))
				.thenThrow(new DuplicateResourceException("Product slug already exists."));

		mockMvc.perform(post("/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "categoryId": "cat-1",
								  "categorySlug": "smartphones",
								  "name": "Phone Pro",
								  "slug": "phone-pro",
								  "brand": "Modern",
								  "model": "Pro",
								  "country": "Moldova",
								  "price": 1000,
								  "currency": "MDL",
								  "stock": 4,
								  "imageUrls": ["/static/assets/images/prod-images/products/phone.png"],
								  "specs": {"memory": "256GB"},
								  "isActive": true
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409));
	}

	private ProductDto product() {
		Instant now = Instant.parse("2026-05-01T12:00:00Z");
		return new ProductDto("prod-1", "cat-1", "smartphones", "Phone Pro", "phone-pro", "Modern", "Pro",
				"Moldova", new BigDecimal("1000.00"), new BigDecimal("900.00"), "MDL", 4,
				List.of("/static/assets/images/prod-images/products/phone.png"), Map.of("memory", "256GB"), true, now, now);
	}
}
