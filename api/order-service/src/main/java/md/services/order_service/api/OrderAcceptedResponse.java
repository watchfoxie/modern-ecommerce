package md.services.order_service.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public acknowledgement returned when an order command is accepted.")
public record OrderAcceptedResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderNumber,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {
}
