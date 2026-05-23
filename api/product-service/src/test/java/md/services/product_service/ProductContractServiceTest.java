package md.services.product_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import md.services.product_service.api.PagedResponseDto;
import md.services.product_service.api.ProductDto;
import md.services.product_service.api.ProductUpsertRequest;
import md.services.product_service.domain.ProductDocument;
import md.services.product_service.exception.DuplicateResourceException;
import md.services.product_service.exception.ResourceNotFoundException;
import md.services.product_service.repository.ProductRepository;
import md.services.product_service.service.ProductContractService;

@ExtendWith(MockitoExtension.class)
class ProductContractServiceTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private MongoTemplate mongoTemplate;

	@InjectMocks
	private ProductContractService productContractService;

	@Test
	void listsActiveProductsWithPaginationEnvelope() {
		ProductDocument product = product("1", "phone", true);
		when(mongoTemplate.count(any(Query.class), eq(ProductDocument.class))).thenReturn(1L);
		when(mongoTemplate.find(any(Query.class), eq(ProductDocument.class))).thenReturn(List.of(product));

		PagedResponseDto<ProductDto> page = productContractService.listProducts("phones", true, 0, 12, "price", "asc");

		assertThat(page.data()).extracting(ProductDto::slug).containsExactly("phone");
		assertThat(page.totalElements()).isEqualTo(1);
		assertThat(page.first()).isTrue();
		assertThat(page.last()).isTrue();
	}

	@Test
	void createsProductWhenSlugIsUnique() {
		when(productRepository.existsBySlug("phone")).thenReturn(false);
		when(productRepository.save(any(ProductDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

		productContractService.createProduct(request("phone"));

		ArgumentCaptor<ProductDocument> productCaptor = ArgumentCaptor.forClass(ProductDocument.class);
		verify(productRepository).save(productCaptor.capture());
		assertThat(productCaptor.getValue().imageUrls()).containsExactly("https://example.test/phone.png");
		assertThat(productCaptor.getValue().createdAt()).isNotNull();
		assertThat(productCaptor.getValue().updatedAt()).isNotNull();
	}

	@Test
	void rejectsDuplicateSlugOnCreate() {
		when(productRepository.existsBySlug("phone")).thenReturn(true);

		assertThatThrownBy(() -> productContractService.createProduct(request("phone")))
				.isInstanceOf(DuplicateResourceException.class);
		verify(productRepository, never()).save(any());
	}

	@Test
	void throwsNotFoundForInactiveOrMissingPublicProduct() {
		when(productRepository.findBySlugAndIsActiveTrue("phone")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> productContractService.getProduct("phone"))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	private ProductUpsertRequest request(String slug) {
		return new ProductUpsertRequest(
				"category-1",
				"phones",
				"Phone",
				slug,
				"Brand",
				"Model",
				"MD",
				BigDecimal.TEN,
				null,
				"MDL",
				5,
				List.of("https://example.test/phone.png"),
				Map.of("screen", "6.1"),
				true);
	}

	private ProductDocument product(String id, String slug, boolean active) {
		Instant now = Instant.now();
		return new ProductDocument(
				id,
				"category-1",
				"phones",
				"Phone",
				slug,
				"Brand",
				"Model",
				"MD",
				BigDecimal.TEN,
				BigDecimal.ONE,
				"MDL",
				5,
				List.of("https://example.test/phone.png"),
				Map.of("screen", "6.1"),
				active,
				now,
				now);
	}

}
