package md.services.category_service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "CATEGORY_SERVICE_USERNAME=test-category-user",
        "CATEGORY_SERVICE_PASSWORD=test-category-password",
        "CATEGORY_MONGODB_URI=mongodb://localhost:27017/category-service-test",
        "app.data.migrations.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class CategoryServiceSecurityAuthenticationTest {

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
}
