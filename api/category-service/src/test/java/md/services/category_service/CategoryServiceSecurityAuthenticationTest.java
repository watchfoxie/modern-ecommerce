package md.services.category_service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import md.services.category_service.config.GatewayHeaderAuthenticationFilter;

@SpringBootTest(properties = {
        "CATEGORY_SERVICE_USERNAME=test-category-user",
        "CATEGORY_SERVICE_PASSWORD=test-category-password",
        "CATEGORY_MONGODB_URI=mongodb://localhost:27017/category-service-test",
        "CATEGORY_SERVICE_DB_NAME=category-service-test",
        "INTERNAL_SERVICE_TOKEN=modern-ecommerce-local-internal-token",
        "app.data.migrations.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class CategoryServiceSecurityAuthenticationTest {

    private static final String INTERNAL_TOKEN = "modern-ecommerce-local-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsRequestsWithoutCredentials() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesActuatorInfoWithoutCredentialsInLocalProfile() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("category-service"))
                .andExpect(jsonPath("$.app.port").value("8083"))
                .andExpect(jsonPath("$.service.role").value("Category catalog bootstrap"))
                .andExpect(jsonPath("$.service.integrations[0]").value("eureka"))
                .andExpect(jsonPath("$.service.integrations[1]").value("mongodb"));
    }

    @Test
    void acceptsConfiguredCredentials() throws Exception {
        mockMvc.perform(get("/").with(httpBasic("test-category-user", "test-category-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDefaultSpringSecurityUsername() throws Exception {
        mockMvc.perform(get("/").with(httpBasic("user", "test-category-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsCategoryWritesFromNonAdminUsers() throws Exception {
        mockMvc.perform(post("/categories")
                .with(httpBasic("test-category-user", "test-category-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Phones",
                          "slug": "phones",
                          "displayOrder": 1,
                          "isActive": true
                        }
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void ignoresSpoofedGatewayIdentityWithoutInternalServiceToken() throws Exception {
        SecurityContextHolder.clearContext();
        GatewayHeaderAuthenticationFilter filter = new GatewayHeaderAuthenticationFilter(INTERNAL_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user-1");
        request.addHeader("X-User-Roles", "ROLE_ADMIN");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsGatewayIdentityWithInternalServiceToken() throws Exception {
        SecurityContextHolder.clearContext();
        GatewayHeaderAuthenticationFilter filter = new GatewayHeaderAuthenticationFilter(INTERNAL_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Token", INTERNAL_TOKEN);
        request.addHeader("X-User-Id", "user-1");
        request.addHeader("X-User-Roles", "ROLE_ADMIN");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        SecurityContextHolder.clearContext();
    }
}
