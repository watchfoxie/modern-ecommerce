package md.services.auth_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import md.services.auth_service.api.AuthController;
import md.services.auth_service.api.AuthIdentityDto;
import md.services.auth_service.api.AuthSignInRequest;
import md.services.auth_service.api.AuthSignUpRequest;
import md.services.auth_service.api.AuthTokenResponse;
import md.services.auth_service.exception.ApiExceptionHandler;
import md.services.auth_service.exception.ConflictException;
import md.services.auth_service.exception.UnauthorizedException;
import md.services.auth_service.service.AuthContractService;

class AuthControllerContractTest {

	private AuthContractService authContractService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		authContractService = mock(AuthContractService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new AuthController(authContractService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void signUpReturnsCreatedIdentityWithoutPasswordHash() throws Exception {
		when(authContractService.signUp(any(AuthSignUpRequest.class))).thenReturn(new AuthIdentityDto(
				"auth-1", "customer@example.com", "ACTIVE", Instant.parse("2026-05-01T12:00:00Z")));

		mockMvc.perform(post("/sign-up")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "firstName": "Ana",
								  "lastName": "Popescu",
								  "email": "customer@example.com",
								  "password": "Password123!"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("auth-1"))
				.andExpect(jsonPath("$.email").value("customer@example.com"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void signInReturnsBearerTokenContract() throws Exception {
		when(authContractService.signIn(any(AuthSignInRequest.class)))
				.thenReturn(new AuthTokenResponse("access-token", "refresh-token", 3600, "Bearer"));

		mockMvc.perform(post("/sign-in")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "customer@example.com",
								  "password": "Password123!"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access-token"))
				.andExpect(jsonPath("$.refreshToken").value("refresh-token"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"));
	}

	@Test
	void invalidSignUpPayloadReturnsProblemDetail() throws Exception {
		mockMvc.perform(post("/sign-up")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "firstName": "",
								  "lastName": "P",
								  "email": "not-email",
								  "password": "short"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Validation failed"));
	}

	@Test
	void duplicateEmailReturnsConflictProblemDetail() throws Exception {
		when(authContractService.signUp(any(AuthSignUpRequest.class)))
				.thenThrow(new ConflictException("Email address is already registered."));

		mockMvc.perform(post("/sign-up")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "firstName": "Ana",
								  "lastName": "Popescu",
								  "email": "customer@example.com",
								  "password": "Password123!"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.title").value("Resource conflict"));
	}

	@Test
	void badCredentialsReturnUnauthorizedProblemDetail() throws Exception {
		when(authContractService.signIn(any(AuthSignInRequest.class)))
				.thenThrow(new UnauthorizedException("Email or password is incorrect."));

		mockMvc.perform(post("/sign-in")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "customer@example.com",
								  "password": "Password123!"
								}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.title").value("Authentication failed"));
	}
}
