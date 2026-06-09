package md.services.product_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
import md.services.product_service.exception.ProductValidationException;
import md.services.product_service.exception.ResourceNotFoundException;
import md.services.product_service.repository.ProductRepository;

@Service
public class ProductContractService {

	private static final String SORT_CREATED_AT = "createdAt";
	private static final String FIELD_PROMOTIONAL_PRICE = "promotionalPrice";
	private static final String PRODUCT_PREFIX = "Product '";
	private static final String NOT_FOUND_SUFFIX = "' was not found.";
	private static final Pattern IMAGE_URL_PATTERN = Pattern.compile("^(?:https?://|/|static/).+");
	private static final Set<String> SORT_FIELDS = Set.of(SORT_CREATED_AT, "updatedAt", "name", "price", "stock");
	private static final Set<String> PERSISTABLE_CATEGORY_SLUGS = Set.of("smartphones", "laptops");
	private static final Set<String> ALLOWED_SPEC_KEYS = Set.of(
			"screenSize",
			"processor",
			"ram",
			"storage",
			"os",
			"battery",
			"camera",
			"gpu",
			"batteryLife");

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
					? Criteria.where(FIELD_PROMOTIONAL_PRICE).gt(BigDecimal.ZERO)
					: new Criteria().orOperator(
							Criteria.where(FIELD_PROMOTIONAL_PRICE).exists(false),
							Criteria.where(FIELD_PROMOTIONAL_PRICE).is(null),
							Criteria.where(FIELD_PROMOTIONAL_PRICE).lte(BigDecimal.ZERO));
			query.addCriteria(promotionCriteria);
		}

		return page(query, page, size, sort, direction);
	}

	public ProductDto getProduct(String slug) {
		return productRepository.findBySlugAndIsActiveTrue(slug)
				.map(this::toDto)
				.orElseThrow(() -> new ResourceNotFoundException(PRODUCT_PREFIX + slug + NOT_FOUND_SUFFIX));
	}

	public ProductInternalDto getInternalProduct(String productId) {
		ProductDocument product = productRepository.findById(productId)
				.or(() -> productRepository.findBySlug(productId))
				.orElseThrow(() -> new ResourceNotFoundException(PRODUCT_PREFIX + productId + NOT_FOUND_SUFFIX));
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
		return page(query, page, size, SORT_CREATED_AT, "desc");
	}

	public ProductDto createProduct(ProductUpsertRequest request) {
		NormalizedProduct normalized = normalize(request);
		if (productRepository.existsBySlug(request.slug())) {
			throw new DuplicateResourceException("Product slug '" + request.slug() + "' already exists.");
		}

		Instant now = Instant.now();
		ProductDocument product = new ProductDocument(
				null,
				normalized.categoryId(),
				normalized.categorySlug(),
				normalized.name(),
				normalized.slug(),
				normalized.brand(),
				normalized.model(),
				normalized.country(),
				normalized.price(),
				normalized.promotionalPrice(),
				normalized.currency(),
				normalized.stock(),
				normalized.imageUrls(),
				normalized.specs(),
				normalized.isActive(),
				now,
				now);
		return toDto(productRepository.save(product));
	}

	public ProductDto updateProduct(String slug, ProductUpsertRequest request) {
		NormalizedProduct normalized = normalize(request);
		ProductDocument existing = productRepository.findBySlug(slug)
				.orElseThrow(() -> new ResourceNotFoundException(PRODUCT_PREFIX + slug + NOT_FOUND_SUFFIX));
		if (!existing.slug().equals(request.slug())
				&& productRepository.existsBySlugAndIdNot(request.slug(), existing.id())) {
			throw new DuplicateResourceException("Product slug '" + request.slug() + "' already exists.");
		}

		ProductDocument updated = new ProductDocument(
				existing.id(),
				normalized.categoryId(),
				normalized.categorySlug(),
				normalized.name(),
				normalized.slug(),
				normalized.brand(),
				normalized.model(),
				normalized.country(),
				normalized.price(),
				normalized.promotionalPrice(),
				normalized.currency(),
				normalized.stock(),
				normalized.imageUrls(),
				normalized.specs(),
				normalized.isActive(),
				existing.createdAt(),
				Instant.now());
		return toDto(productRepository.save(updated));
	}

	public void deleteProduct(String slug) {
		ProductDocument existing = productRepository.findBySlug(slug)
				.orElseThrow(() -> new ResourceNotFoundException(PRODUCT_PREFIX + slug + NOT_FOUND_SUFFIX));
		productRepository.delete(existing);
	}

	private NormalizedProduct normalize(ProductUpsertRequest request) {
		String categoryId = trim(request.categoryId(), "categoryId: Category id is required.");
		String categorySlug = trim(request.categorySlug(), "categorySlug: Product category is required.");
		if (!PERSISTABLE_CATEGORY_SLUGS.contains(categorySlug)) {
			throw new ProductValidationException(
					"categorySlug: Categoria produsului trebuie să fie `smartphones` sau `laptops`. `offers` este o categorie virtuală.");
		}

		List<String> imageUrls = request.imageUrls().stream()
				.map(imageUrl -> trim(imageUrl, "imageUrls: Every image path is required."))
				.peek(imageUrl -> {
					if (!IMAGE_URL_PATTERN.matcher(imageUrl).matches()) {
						throw new ProductValidationException(
								"imageUrls: Each image must be an absolute URL, an application-relative path, or a static/ asset path.");
					}
				})
				.toList();

		Map<String, String> specs = normalizeSpecs(request.specs());
		BigDecimal promotionalPrice = normalizePromotionalPrice(request.price(), request.promotionalPrice());

		return new NormalizedProduct(
				categoryId,
				categorySlug,
				trim(request.name(), "name: Product name is required."),
				trim(request.slug(), "slug: Product slug is required."),
				trim(request.brand(), "brand: Product brand is required."),
				trim(request.model(), "model: Product model is required."),
				trim(request.country(), "country: Country is required."),
				request.price(),
				promotionalPrice,
				trim(request.currency(), "currency: Currency is required."),
				request.stock(),
				List.copyOf(imageUrls),
				Map.copyOf(specs),
				request.isActive());
	}

	private Map<String, String> normalizeSpecs(Map<String, String> rawSpecs) {
		LinkedHashMap<String, String> normalizedSpecs = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : rawSpecs.entrySet()) {
			String key = trim(entry.getKey(), "specs: Specification key is required.");
			if (!ALLOWED_SPEC_KEYS.contains(key)) {
				throw new ProductValidationException(
						"specs: Unsupported specification `" + key + "`. Allowed keys: "
								+ String.join(", ", ALLOWED_SPEC_KEYS) + ".");
			}
			normalizedSpecs.put(key, trim(entry.getValue(), "specs: Specification values are required."));
		}

		if (normalizedSpecs.isEmpty()) {
			throw new ProductValidationException("specs: At least one specification is required.");
		}

		return normalizedSpecs;
	}

	private BigDecimal normalizePromotionalPrice(BigDecimal price, BigDecimal promotionalPrice) {
		if (promotionalPrice == null) {
			return null;
		}

		if (promotionalPrice.compareTo(price) > 0) {
			throw new ProductValidationException(
					"promotionalPrice: Promotional price must be lower than or equal to the standard price.");
		}

		if (promotionalPrice.compareTo(price) == 0) {
			return null;
		}

		return promotionalPrice;
	}

	private String trim(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new ProductValidationException(message);
		}
		return value.trim();
	}

	private Query activeProductsQuery() {
		return Query.query(Criteria.where("isActive").is(true));
	}

	private PagedResponseDto<ProductDto> page(Query query, int page, int size, String sort, String direction) {
		Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
		String sortField = SORT_FIELDS.contains(sort) ? sort : SORT_CREATED_AT;
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

	private record NormalizedProduct(
			String categoryId,
			String categorySlug,
			String name,
			String slug,
			String brand,
			String model,
			String country,
			BigDecimal price,
			BigDecimal promotionalPrice,
			String currency,
			Integer stock,
			List<String> imageUrls,
			Map<String, String> specs,
			Boolean isActive) {
	}

}
