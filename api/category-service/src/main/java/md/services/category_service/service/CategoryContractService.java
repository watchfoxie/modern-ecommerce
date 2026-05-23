package md.services.category_service.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import md.services.category_service.api.CategoryDto;
import md.services.category_service.api.PagedResponseDto;
import md.services.category_service.api.CategoryUpsertRequest;
import md.services.category_service.domain.CategoryDocument;
import md.services.category_service.exception.DuplicateResourceException;
import md.services.category_service.exception.ResourceNotFoundException;
import md.services.category_service.repository.CategoryRepository;

@Service
public class CategoryContractService {

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
				.orElseThrow(() -> new ResourceNotFoundException("Category '" + slug + "' was not found."));
	}

	public CategoryDto createCategory(CategoryUpsertRequest request) {
		if (categoryRepository.existsBySlug(request.slug())) {
			throw new DuplicateResourceException("Category slug '" + request.slug() + "' already exists.");
		}

		Instant now = Instant.now();
		CategoryDocument category = new CategoryDocument(
				null,
				request.slug(),
				request.name(),
				blankToNull(request.parentId()),
				request.description(),
				request.imageUrl(),
				request.displayOrder(),
				request.isActive(),
				now,
				now);
		return toDto(categoryRepository.save(category));
	}

	public CategoryDto updateCategory(String slug, CategoryUpsertRequest request) {
		CategoryDocument existing = categoryRepository.findBySlug(slug)
				.orElseThrow(() -> new ResourceNotFoundException("Category '" + slug + "' was not found."));
		if (!existing.slug().equals(request.slug()) && categoryRepository.existsBySlugAndIdNot(request.slug(), existing.id())) {
			throw new DuplicateResourceException("Category slug '" + request.slug() + "' already exists.");
		}

		CategoryDocument updated = new CategoryDocument(
				existing.id(),
				request.slug(),
				request.name(),
				blankToNull(request.parentId()),
				request.description(),
				request.imageUrl(),
				request.displayOrder(),
				request.isActive(),
				existing.createdAt(),
				Instant.now());
		return toDto(categoryRepository.save(updated));
	}

	public void deleteCategory(String slug) {
		CategoryDocument existing = categoryRepository.findBySlug(slug)
				.orElseThrow(() -> new ResourceNotFoundException("Category '" + slug + "' was not found."));
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

}
