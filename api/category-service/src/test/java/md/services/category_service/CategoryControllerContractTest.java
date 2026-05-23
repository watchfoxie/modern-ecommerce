package md.services.category_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import md.services.category_service.api.CategoryController;
import md.services.category_service.api.CategoryDto;
import md.services.category_service.api.CategoryUpsertRequest;
import md.services.category_service.exception.ApiExceptionHandler;
import md.services.category_service.exception.DuplicateResourceException;
import md.services.category_service.exception.ResourceNotFoundException;
import md.services.category_service.service.CategoryContractService;

class CategoryControllerContractTest {

	private CategoryContractService categoryContractService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		categoryContractService = mock(CategoryContractService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new CategoryController(categoryContractService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void listCategoriesReturnsPublicDtos() throws Exception {
		when(categoryContractService.listCategories(null)).thenReturn(List.of(category()));

		mockMvc.perform(get("/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].slug").value("smartphones"))
				.andExpect(jsonPath("$[0].isActive").value(true));
	}

	@Test
	void missingCategoryReturnsNotFoundProblemDetail() throws Exception {
		when(categoryContractService.getCategory("missing")).thenThrow(new ResourceNotFoundException("Category was not found."));

		mockMvc.perform(get("/categories/missing"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("Resource not found"));
	}

	@Test
	void duplicateSlugReturnsConflictProblemDetail() throws Exception {
		when(categoryContractService.createCategory(any(CategoryUpsertRequest.class)))
				.thenThrow(new DuplicateResourceException("Category slug already exists."));

		mockMvc.perform(post("/categories")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Smartphones",
								  "slug": "smartphones",
								  "displayOrder": 1,
								  "isActive": true
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409));
	}

	private CategoryDto category() {
		Instant now = Instant.parse("2026-05-01T12:00:00Z");
		return new CategoryDto("cat-1", "Smartphones", "smartphones", null, "Phones", "/phones.png", 1, true, now, now);
	}
}
