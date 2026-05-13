package md.services.product_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import md.services.product_service.api.PagedResponseDto;
import md.services.product_service.api.ProductDto;
import md.services.product_service.api.ProductInternalDto;
import md.services.product_service.api.ProductUpsertRequest;
import md.services.product_service.domain.ProductDocument;
import md.services.product_service.exception.DuplicateResourceException;
import md.services.product_service.exception.ResourceNotFoundException;
import md.services.product_service.repository.ProductRepository;

@Service
public class ProductContractService {

	private static final Set<String> SORT_FIELDS = Set.of("createdAt", "updatedAt", "name", "price", "stock");

	private final ProductRepository productRepository;
	private final MongoTemplate mongoTemplate;

	public ProductContractService(ProductRepository productRepository, MongoTemplate mongoTemplate) {
		this.productRepository = productRepository;
		this.mongoTemplate = mongoTemplate;
	}

	public PagedResponseDto<ProductDto> listProducts(String categorySlug, Boolean hasPromotion, int page, int size,
			String sort, String direction) {
		Query query = activeProductsQuery();
		if (categorySlug != null && !categorySlug.isBlank()) {
			query.addCriteria(Criteria.where("categorySlug").is(categorySlug));
		}
		if (hasPromotion != null) {
			Criteria promotionCriteria = Boolean.TRUE.equals(hasPromotion)
					? Criteria.where("promotionalPrice").gt(BigDecimal.ZERO)
					: new Criteria().orOperator(
							Criteria.where("promotionalPrice").exists(false),
							Criteria.where("promotionalPrice").is(null),
							Criteria.where("promotionalPrice").lte(BigDecimal.ZERO));
			query.addCriteria(promotionCriteria);
		}

		return page(query, page, size, sort, direction);
	}

	public ProductDto getProduct(String slug) {
		return productRepository.findBySlugAndIsActiveTrue(slug)
				.map(this::toDto)
				.orElseThrow(() -> new ResourceNotFoundException("Product '" + slug + "' was not found."));
	}

	public ProductInternalDto getInternalProduct(String productId) {
		ProductDocument product = productRepository.findById(productId)
				.or(() -> productRepository.findBySlug(productId))
				.orElseThrow(() -> new ResourceNotFoundException("Product '" + productId + "' was not found."));
		return new ProductInternalDto(
				product.id(),
				product.name(),
				product.brand(),
				product.categorySlug(),
				product.price(),
				product.promotionalPrice(),
				product.currency(),
				product.stock(),
				product.imageUrls(),
				product.isActive());
	}

	public PagedResponseDto<ProductDto> searchProducts(String q, int page, int size) {
		Query query = activeProductsQuery();
		query.addCriteria(TextCriteria.forDefaultLanguage().matching(q.trim()));
		return page(query, page, size, "createdAt", "desc");
	}

	public ProductDto createProduct(ProductUpsertRequest request) {
		if (productRepository.existsBySlug(request.slug())) {
			throw new DuplicateResourceException("Product slug '" + request.slug() + "' already exists.");
		}

		Instant now = Instant.now();
		ProductDocument product = new ProductDocument(
				null,
				request.categoryId(),
				request.categorySlug(),
				request.name(),
				request.slug(),
				request.brand(),
				request.model(),
				request.country(),
				request.price(),
				request.promotionalPrice(),
				request.currency(),
				request.stock(),
				List.copyOf(request.imageUrls()),
				Map.copyOf(request.specs()),
				request.isActive(),
				now,
				now);
		return toDto(productRepository.save(product));
	}

	public ProductDto updateProduct(String slug, ProductUpsertRequest request) {
		ProductDocument existing = productRepository.findBySlug(slug)
				.orElseThrow(() -> new ResourceNotFoundException("Product '" + slug + "' was not found."));
		if (!existing.slug().equals(request.slug()) && productRepository.existsBySlugAndIdNot(request.slug(), existing.id())) {
			throw new DuplicateResourceException("Product slug '" + request.slug() + "' already exists.");
		}

		ProductDocument updated = new ProductDocument(
				existing.id(),
				request.categoryId(),
				request.categorySlug(),
				request.name(),
				request.slug(),
				request.brand(),
				request.model(),
				request.country(),
				request.price(),
				request.promotionalPrice(),
				request.currency(),
				request.stock(),
				List.copyOf(request.imageUrls()),
				Map.copyOf(request.specs()),
				request.isActive(),
				existing.createdAt(),
				Instant.now());
		return toDto(productRepository.save(updated));
	}

	public void deleteProduct(String slug) {
		ProductDocument existing = productRepository.findBySlug(slug)
				.orElseThrow(() -> new ResourceNotFoundException("Product '" + slug + "' was not found."));
		productRepository.delete(existing);
	}

	private Query activeProductsQuery() {
		return Query.query(Criteria.where("isActive").is(true));
	}

	private PagedResponseDto<ProductDto> page(Query query, int page, int size, String sort, String direction) {
		Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
		String sortField = SORT_FIELDS.contains(sort) ? sort : "createdAt";
		Query countQuery = Query.of(query).limit(-1).skip(-1);
		long total = mongoTemplate.count(countQuery, ProductDocument.class);
		List<ProductDto> content = mongoTemplate.find(
						Query.of(query).with(PageRequest.of(page, size, Sort.by(sortDirection, sortField))),
						ProductDocument.class)
				.stream()
				.map(this::toDto)
				.toList();
		int totalPages = (int) Math.ceil((double) total / size);
		return new PagedResponseDto<>(content, page, size, total, totalPages, page == 0, page >= totalPages - 1);
	}

	private ProductDto toDto(ProductDocument product) {
		return new ProductDto(
				product.id(),
				product.categoryId(),
				product.categorySlug(),
				product.name(),
				product.slug(),
				product.brand(),
				product.model(),
				product.country(),
				product.price(),
				product.promotionalPrice(),
				product.currency(),
				product.stock(),
				product.imageUrls(),
				product.specs(),
				product.isActive(),
				product.createdAt(),
				product.updatedAt());
	}

}
