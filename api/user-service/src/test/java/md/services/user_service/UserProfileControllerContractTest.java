package md.services.user_service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import md.services.user_service.api.AddressDto;
import md.services.user_service.api.PreferencesDto;
import md.services.user_service.api.UserProfileController;
import md.services.user_service.api.UserProfileDto;
import md.services.user_service.exception.ApiExceptionHandler;
import md.services.user_service.service.UserContractService;

class UserProfileControllerContractTest {

	private UserContractService userContractService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		userContractService = mock(UserContractService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new UserProfileController(userContractService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void getCurrentUserProfileReturnsDtoFromGatewayIdentity() throws Exception {
		when(userContractService.getCurrentUserProfile("auth-1")).thenReturn(profile());

		mockMvc.perform(get("/users/me").header("X-Auth-Id", "auth-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authId").value("auth-1"))
				.andExpect(jsonPath("$.email").value("customer@example.com"))
				.andExpect(jsonPath("$.addresses[0].city").value("Chisinau"));
	}

	@Test
	void missingUserProfileReturnsProblemDetail() throws Exception {
		when(userContractService.getCurrentUserProfile("auth-404"))
				.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile was not found."));

		mockMvc.perform(get("/users/me").header("X-Auth-Id", "auth-404"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("Resource not found"));
	}

	@Test
	void invalidProfileUpdateReturnsValidationProblemDetail() throws Exception {
		mockMvc.perform(put("/users/me")
						.header("X-Auth-Id", "auth-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "firstName": "",
								  "lastName": "Popescu",
								  "phone": "not-a-phone"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors").isArray());
	}

	private UserProfileDto profile() {
		OffsetDateTime now = OffsetDateTime.parse("2026-05-01T12:00:00Z");
		return new UserProfileDto(
				"user-1",
				"auth-1",
				"customer@example.com",
				"Ana",
				"Popescu",
				"+37360000000",
				LocalDate.parse("1995-01-20"),
				List.of(new AddressDto("Home", "Main 1", "Chisinau", "Centru", "2001", true)),
				new PreferencesDto("ro", "MDL"),
				now,
				now);
	}
}
