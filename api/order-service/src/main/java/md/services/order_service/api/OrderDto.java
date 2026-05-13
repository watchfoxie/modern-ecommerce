package md.services.order_service.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public order representation.")
public record OrderDto(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String userId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderNumber,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrderItemDto> items,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) DeliveryAddressDto deliveryAddress,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) PaymentDto payment,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalAmount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String currency,
		String notes,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt) {

	public record OrderItemDto(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String brand,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String imageUrl,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int quantity,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal unitPrice) {
	}

	public record DeliveryAddressDto(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String street,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String city,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String district,
			String postalCode,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String recipientName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String recipientPhone) {
	}

	public record PaymentDto(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String method,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
			String transactionId) {
	}

}
