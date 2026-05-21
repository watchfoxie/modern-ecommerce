package md.services.user_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "USER_SERVICE_USERNAME=test-user-service-user",
        "USER_SERVICE_PASSWORD=test-user-service-password",
        "USER_MONGODB_URI=mongodb://localhost:27017/user-service-test",
        "USER_SERVICE_DB_NAME=user-service-test",
        "app.data.migrations.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class UserServiceSecurityAuthenticationTest {

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
                .andExpect(jsonPath("$.app.name").value("user-service"))
                .andExpect(jsonPath("$.app.port").value("8082"))
                .andExpect(jsonPath("$.service.role").value("User profile bootstrap"))
                .andExpect(jsonPath("$.service.integrations[0]").value("eureka"))
                .andExpect(jsonPath("$.service.integrations[1]").value("mongodb"));
    }

    @Test
    void acceptsConfiguredInternalServiceToken() throws Exception {
        mockMvc.perform(get("/").header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidInternalServiceToken() throws Exception {
        mockMvc.perform(get("/").header("X-Internal-Service-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
    }
}
