package md.services.product_service.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import md.services.product_service.service.ProductContractService;

@RestController
@RequestMapping("/products")
@Tag(name = "Products")
public class ProductController {

	private final ProductContractService productContractService;

	public ProductController(ProductContractService productContractService) {
		this.productContractService = productContractService;
	}

	@GetMapping
	@SecurityRequirements
	@Operation(summary = "List products with filtering and pagination")
	public PagedResponseDto<ProductDto> listProducts(
			@RequestParam(required = false) String categorySlug,
			@RequestParam(required = false) Boolean hasPromotion,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "12") @Min(1) int size,
			@RequestParam(defaultValue = "createdAt") String sort,
			@RequestParam(defaultValue = "desc") String direction) {
		return productContractService.listProducts(categorySlug, hasPromotion, page, size, sort, direction);
	}

	@GetMapping("/{slug}")
	@SecurityRequirements
	@Operation(summary = "Get product by public slug")
	public ProductDto getProduct(@PathVariable String slug) {
		return productContractService.getProduct(slug);
	}

	@GetMapping("/search")
	@SecurityRequirements
	@Operation(summary = "Search products by text query")
	public PagedResponseDto<ProductDto> searchProducts(
			@RequestParam @NotBlank String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "12") @Min(1) int size) {
		return productContractService.searchProducts(q, page, size);
	}

	@PostMapping
	@Operation(summary = "Create product as administrator")
	public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody ProductUpsertRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(productContractService.createProduct(request));
	}

	@PutMapping("/{slug}")
	@Operation(summary = "Update product as administrator")
	public ProductDto updateProduct(@PathVariable String slug, @Valid @RequestBody ProductUpsertRequest request) {
		return productContractService.updateProduct(slug, request);
	}

	@DeleteMapping("/{slug}")
	@Operation(summary = "Delete product as administrator")
	public ResponseEntity<Void> deleteProduct(@PathVariable String slug) {
		productContractService.deleteProduct(slug);
		return ResponseEntity.noContent().build();
	}

}
