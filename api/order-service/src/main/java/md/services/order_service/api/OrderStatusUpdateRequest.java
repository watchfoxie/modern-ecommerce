package md.services.order_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Administrative request for changing order status.")
public record OrderStatusUpdateRequest(
		@NotBlank String status) {
}
