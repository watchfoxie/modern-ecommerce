package md.services.order_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import md.services.order_service.api.OrderDto;
import md.services.order_service.api.OrderDto.DeliveryAddressDto;
import md.services.order_service.api.OrderDto.OrderItemDto;
import md.services.order_service.api.OrderDto.PaymentDto;
import md.services.order_service.api.OrderStatusUpdateRequest;
import md.services.order_service.api.PagedResponseDto;
import md.services.order_service.service.OrderContractService;

@SpringBootTest(properties = {
		"ORDER_MONGODB_URI=mongodb://localhost:27017/order-service-test",
		"ORDER_SERVICE_DB_NAME=order-service-test",
		"app.data.migrations.enabled=false",
		"spring.rabbitmq.dynamic=false",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class OrderServiceSecurityConfigurationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderContractService orderContractService;

	@Test
	void rejectsBusinessRequestsWithoutGatewayIdentity() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isForbidden());
	}

	@Test
	void acceptsGatewayInternalIdentityWithoutBasicAuthChallenge() throws Exception {
		mockMvc.perform(get("/")
				.header("X-Internal-Service-Token", "modern-ecommerce-local-internal-token")
				.header("X-User-Id", "user-1")
				.header("X-User-Roles", "ROLE_USER"))
				.andExpect(status().isNotFound());
	}

	@Test
	void exposesReadinessHealthWithoutBasicAuthChallenge() throws Exception {
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk());
	}

	@Test
	void exposesActuatorInfoPayloadWithoutBasicAuthChallenge() throws Exception {
		mockMvc.perform(get("/actuator/info"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.app.name").value("order-service"))
				.andExpect(jsonPath("$.app.port").value("8086"))
				.andExpect(jsonPath("$.service.role").value("Order intake and event publication"))
				.andExpect(jsonPath("$.service.integrations[0]").value("eureka"))
				.andExpect(jsonPath("$.service.integrations[1]").value("mongodb"))
				.andExpect(jsonPath("$.service.integrations[2]").value("rabbitmq"));
	}

	@Test
	void rejectsAdministrativeOrderListingForNonAdminIdentity() throws Exception {
		mockMvc.perform(get("/orders/all")
				.header("X-Internal-Service-Token", "modern-ecommerce-local-internal-token")
				.header("X-User-Id", "user-1")
				.header("X-User-Roles", "ROLE_USER"))
				.andExpect(status().isForbidden());
	}

	@Test
	void acceptsAdministrativeOrderListingForAdminIdentity() throws Exception {
		when(orderContractService.listAllOrders(0, 20, null))
				.thenReturn(new PagedResponseDto<>(List.of(sampleOrder()), 0, 20, 1, 1, true, true));

		mockMvc.perform(get("/orders/all")
				.header("X-Internal-Service-Token", "modern-ecommerce-local-internal-token")
				.header("X-User-Id", "admin-1")
				.header("X-User-Roles", "ROLE_ADMIN"))
				.andExpect(status().isOk());
	}

	@Test
	void rejectsAdministrativeStatusUpdateForNonAdminIdentity() throws Exception {
		mockMvc.perform(patch("/orders/order-1/status")
				.header("X-Internal-Service-Token", "modern-ecommerce-local-internal-token")
				.header("X-User-Id", "user-1")
				.header("X-User-Roles", "ROLE_USER")
				.contentType("application/json")
				.content("{\"status\":\"CONFIRMED\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void acceptsAdministrativeStatusUpdateForAdminIdentity() throws Exception {
		when(orderContractService.updateStatus(eq("order-1"), any(OrderStatusUpdateRequest.class)))
				.thenReturn(sampleOrder());

		mockMvc.perform(patch("/orders/order-1/status")
				.header("X-Internal-Service-Token", "modern-ecommerce-local-internal-token")
				.header("X-User-Id", "admin-1")
				.header("X-User-Roles", "ROLE_ADMIN")
				.contentType("application/json")
				.content("{\"status\":\"CONFIRMED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("order-1"));
	}

	private OrderDto sampleOrder() {
		OffsetDateTime now = OffsetDateTime.parse("2026-05-01T12:00:00Z");
		return new OrderDto(
				"order-1",
				"user-1",
				"ORD-1",
				List.of(new OrderItemDto("prod-1", "Phone Pro", "Modern", "/phone.png", 1, new BigDecimal("1000.00"))),
				new DeliveryAddressDto("Main 1", "Chisinau", "Centru", "2001", "Ana Popescu", "+37360000000"),
				new PaymentDto("CARD", "PENDING", "tx-1"),
				"CONFIRMED",
				new BigDecimal("1000.00"),
				"MDL",
				"leave at door",
				now,
				now);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		@Primary
		OrderContractService orderContractService() {
			return org.mockito.Mockito.mock(OrderContractService.class);
		}
	}

}
