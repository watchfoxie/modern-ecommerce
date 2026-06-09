package md.services.category_service.service;

import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import md.services.category_service.api.CategoryDto;
import md.services.category_service.api.PagedResponseDto;
import md.services.category_service.api.CategoryUpsertRequest;
import md.services.category_service.domain.CategoryDocument;
import md.services.category_service.exception.CategoryValidationException;
import md.services.category_service.exception.DuplicateResourceException;
import md.services.category_service.exception.ResourceNotFoundException;
import md.services.category_service.repository.CategoryRepository;

@Service
public class CategoryContractService {

	private static final String CATEGORY_PREFIX = "Category '";
	private static final String NOT_FOUND_SUFFIX = "' was not found.";
	private static final Pattern IMAGE_URL_PATTERN = Pattern.compile("^(?:https?://|/|static/).+");

	private final CategoryRepository categoryRepository;

	public CategoryContractService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	public PagedResponseDto<CategoryDto> listCategories(String parentId, int page, int size) {
		PageRequest pageable = PageRequest.of(page, size, Sort.by(
				Sort.Order.asc("displayOrder"),
				Sort.Order.asc("name")));
		Page<CategoryDocument> categories = (parentId == null || parentId.isBlank())
				? categoryRepository.findByIsActiveTrue(pageable)
				: categoryRepository.findByParentIdAndIsActiveTrue(parentId, pageable);
		return new PagedResponseDto<>(
				categories.getContent().stream().map(this::toDto).toList(),
				categories.getNumber(),
				categories.getSize(),
				categories.getTotalElements(),
				categories.getTotalPages(),
				categories.isFirst(),
				categories.isLast());
	}

	public CategoryDto getCategory(String slug) {
		return categoryRepository.findBySlugAndIsActiveTrue(slug)
				.map(this::toDto)
				.orElseThrow(() -> new ResourceNotFoundException(CATEGORY_PREFIX + slug + NOT_FOUND_SUFFIX));
	}

	public CategoryDto createCategory(CategoryUpsertRequest request) {
		NormalizedCategory normalized = normalize(request, null);
		if (categoryRepository.existsBySlug(normalized.slug())) {
			throw new DuplicateResourceException("Category slug '" + normalized.slug() + "' already exists.");
		}

		Instant now = Instant.now();
		CategoryDocument category = new CategoryDocument(
				null,
				normalized.slug(),
				normalized.name(),
				normalized.parentId(),
				normalized.description(),
				normalized.imageUrl(),
				normalized.displayOrder(),
				normalized.isActive(),
				now,
				now);
		return toDto(categoryRepository.save(category));
	}

	public CategoryDto updateCategory(String slug, CategoryUpsertRequest request) {
		CategoryDocument existing = categoryRepository.findBySlug(slug)
				.orElseThrow(() -> new ResourceNotFoundException(CATEGORY_PREFIX + slug + NOT_FOUND_SUFFIX));
		NormalizedCategory normalized = normalize(request, existing.id());
		if (!existing.slug().equals(normalized.slug())
				&& categoryRepository.existsBySlugAndIdNot(normalized.slug(), existing.id())) {
			throw new DuplicateResourceException("Category slug '" + normalized.slug() + "' already exists.");
		}

		CategoryDocument updated = new CategoryDocument(
				existing.id(),
				normalized.slug(),
				normalized.name(),
				normalized.parentId(),
				normalized.description(),
				normalized.imageUrl(),
				normalized.displayOrder(),
				normalized.isActive(),
				existing.createdAt(),
				Instant.now());
		return toDto(categoryRepository.save(updated));
	}

	public void deleteCategory(String slug) {
		CategoryDocument existing = categoryRepository.findBySlug(slug)
				.orElseThrow(() -> new ResourceNotFoundException(CATEGORY_PREFIX + slug + NOT_FOUND_SUFFIX));
		categoryRepository.delete(existing);
	}

	private CategoryDto toDto(CategoryDocument category) {
		return new CategoryDto(
				category.id(),
				category.name(),
				category.slug(),
				category.parentId(),
				category.description(),
				category.imageUrl(),
				category.displayOrder(),
				category.isActive(),
				category.createdAt(),
				category.updatedAt());
	}

	private String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	private NormalizedCategory normalize(CategoryUpsertRequest request, String existingCategoryId) {
		String name = trim(request.name(), "name: Category name is required.");
		String slug = trim(request.slug(), "slug: Category slug is required.");
		String description = trim(request.description(), "description: Category description is required.");
		String parentId = blankToNull(request.parentId());
		String imageUrl = blankToNull(request.imageUrl());

		if (parentId != null) {
			if (parentId.equals(existingCategoryId)) {
				throw new CategoryValidationException("parentId: Categoria nu se poate selecta pe sine drept părinte.");
			}

			categoryRepository.findById(parentId)
					.orElseThrow(
							() -> new CategoryValidationException("parentId: Categoria părinte selectată nu există."));
		}

		if (imageUrl != null && !IMAGE_URL_PATTERN.matcher(imageUrl).matches()) {
			throw new CategoryValidationException(
					"imageUrl: Imaginea trebuie să fie un URL valid sau o cale statică din proiect.");
		}

		return new NormalizedCategory(slug, name, parentId, description, imageUrl, request.displayOrder(),
				request.isActive());
	}

	private String trim(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new CategoryValidationException(message);
		}

		return value.trim();
	}

	private record NormalizedCategory(
			String slug,
			String name,
			String parentId,
			String description,
			String imageUrl,
			Integer displayOrder,
			Boolean isActive) {
	}

}
