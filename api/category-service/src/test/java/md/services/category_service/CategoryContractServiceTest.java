package md.services.category_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import md.services.category_service.api.CategoryUpsertRequest;
import md.services.category_service.domain.CategoryDocument;
import md.services.category_service.exception.DuplicateResourceException;
import md.services.category_service.exception.ResourceNotFoundException;
import md.services.category_service.repository.CategoryRepository;
import md.services.category_service.service.CategoryContractService;

@ExtendWith(MockitoExtension.class)
class CategoryContractServiceTest {

	@Mock
	private CategoryRepository categoryRepository;

	@InjectMocks
	private CategoryContractService categoryContractService;

	@Test
	void listsOnlyActiveCategoriesWithParentFilter() {
		Instant now = Instant.now();
		when(categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAscNameAsc("root"))
				.thenReturn(List.of(new CategoryDocument(
						"1", "phones", "Phones", "root", "Mobile phones", "/phones.png", 10, true, now, now)));

		List<?> categories = categoryContractService.listCategories("root");

		assertThat(categories).hasSize(1);
		verify(categoryRepository).findByParentIdAndIsActiveTrueOrderByDisplayOrderAscNameAsc("root");
	}

	@Test
	void createsCategoryWhenSlugIsUnique() {
		when(categoryRepository.existsBySlug("phones")).thenReturn(false);
		when(categoryRepository.save(any(CategoryDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

		categoryContractService.createCategory(new CategoryUpsertRequest(
				"Phones", "phones", " ", "Mobile phones", "/phones.png", 10, true));

		ArgumentCaptor<CategoryDocument> categoryCaptor = ArgumentCaptor.forClass(CategoryDocument.class);
		verify(categoryRepository).save(categoryCaptor.capture());
		assertThat(categoryCaptor.getValue().parentId()).isNull();
		assertThat(categoryCaptor.getValue().createdAt()).isNotNull();
		assertThat(categoryCaptor.getValue().updatedAt()).isNotNull();
	}

	@Test
	void rejectsDuplicateSlugOnCreate() {
		when(categoryRepository.existsBySlug("phones")).thenReturn(true);

		assertThatThrownBy(() -> categoryContractService.createCategory(new CategoryUpsertRequest(
				"Phones", "phones", null, null, null, 10, true)))
				.isInstanceOf(DuplicateResourceException.class);
		verify(categoryRepository, never()).save(any());
	}

	@Test
	void throwsNotFoundForInactiveOrMissingPublicCategory() {
		when(categoryRepository.findBySlugAndIsActiveTrue("phones")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> categoryContractService.getCategory("phones"))
				.isInstanceOf(ResourceNotFoundException.class);
	}

}
