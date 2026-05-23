package md.services.category_service.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
import md.services.category_service.service.CategoryContractService;

@Validated
@RestController
@RequestMapping({"/categories", "/v1/categories"})
@Tag(name = "Categories")
public class CategoryController {

	private final CategoryContractService categoryContractService;

	public CategoryController(CategoryContractService categoryContractService) {
		this.categoryContractService = categoryContractService;
	}

	@GetMapping
	@SecurityRequirements
	@Operation(summary = "List active categories with offset pagination")
	public PagedResponseDto<CategoryDto> listCategories(
			@RequestParam(required = false) String parentId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) int size) {
		return categoryContractService.listCategories(parentId, page, size);
	}

	@GetMapping("/{slug}")
	@SecurityRequirements
	@Operation(summary = "Get category by slug")
	public CategoryDto getCategory(@PathVariable @NotBlank String slug) {
		return categoryContractService.getCategory(slug);
	}

	@PostMapping
	@Operation(summary = "Create category as administrator")
	public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CategoryUpsertRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(categoryContractService.createCategory(request));
	}

	@PutMapping("/{slug}")
	@Operation(summary = "Update category as administrator")
	public CategoryDto updateCategory(@PathVariable @NotBlank String slug, @Valid @RequestBody CategoryUpsertRequest request) {
		return categoryContractService.updateCategory(slug, request);
	}

	@DeleteMapping("/{slug}")
	@Operation(summary = "Delete category as administrator")
	public ResponseEntity<Void> deleteCategory(@PathVariable @NotBlank String slug) {
		categoryContractService.deleteCategory(slug);
		return ResponseEntity.noContent().build();
	}

}
