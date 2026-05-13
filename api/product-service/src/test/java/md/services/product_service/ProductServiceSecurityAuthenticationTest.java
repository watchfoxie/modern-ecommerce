package md.services.product_service;

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
        "PRODUCT_SERVICE_USERNAME=test-product-user",
        "PRODUCT_SERVICE_PASSWORD=test-product-password",
        "PRODUCT_MONGODB_URI=mongodb://localhost:27017/product-service-test",
        "app.data.migrations.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class ProductServiceSecurityAuthenticationTest {

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
                .andExpect(jsonPath("$.app.name").value("product-service"))
                .andExpect(jsonPath("$.app.port").value("8084"))
                .andExpect(jsonPath("$.service.role").value("Product catalog bootstrap"))
                .andExpect(jsonPath("$.service.integrations[0]").value("eureka"))
                .andExpect(jsonPath("$.service.integrations[1]").value("mongodb"));
    }

    @Test
    void acceptsConfiguredCredentials() throws Exception {
        mockMvc.perform(get("/").with(httpBasic("test-product-user", "test-product-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDefaultSpringSecurityUsername() throws Exception {
        mockMvc.perform(get("/").with(httpBasic("user", "test-product-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsProductWritesFromNonAdminUsers() throws Exception {
        mockMvc.perform(post("/products")
                .with(httpBasic("test-product-user", "test-product-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "categoryId": "category-1",
                          "categorySlug": "phones",
                          "name": "Phone",
                          "slug": "phone",
                          "brand": "Brand",
                          "model": "Model",
                          "country": "MD",
                          "price": 10.00,
                          "currency": "MDL",
                          "stock": 3,
                          "imageUrls": ["https://example.test/phone.png"],
                          "specs": {},
                          "isActive": true
                        }
                        """))
                .andExpect(status().isForbidden());
    }
}
