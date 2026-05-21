package md.services.auth_service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "AUTH_SERVICE_USERNAME=test-auth-user",
        "AUTH_SERVICE_PASSWORD=test-auth-password",
        "AUTH_MONGODB_URI=mongodb://localhost:27017/auth-service-test",
        "AUTH_SERVICE_DB_NAME=auth-service-test",
        "app.data.migrations.enabled=false",
        "app.startup.mongo-verification.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class AuthServiceSecurityAuthenticationTest {

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
                .andExpect(jsonPath("$.app.name").value("auth-service"))
                .andExpect(jsonPath("$.app.port").value("8081"))
                .andExpect(jsonPath("$.service.role").value("Authentication and identity bootstrap"))
                .andExpect(jsonPath("$.service.integrations[0]").value("eureka"))
                .andExpect(jsonPath("$.service.integrations[1]").value("mongodb"));
    }

    @Test
    void acceptsConfiguredCredentials() throws Exception {
        mockMvc.perform(get("/").with(httpBasic("test-auth-user", "test-auth-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDefaultSpringSecurityUsername() throws Exception {
        mockMvc.perform(get("/").with(httpBasic("user", "test-auth-password")))
                .andExpect(status().isUnauthorized());
    }
}
