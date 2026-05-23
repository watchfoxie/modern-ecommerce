package md.services.order_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Administrative request for changing order status.")
public record OrderStatusUpdateRequest(
		@NotBlank
		@Pattern(regexp = "CREATED|CONFIRMED|PROCESSING|SHIPPED|DELIVERED|CANCELLED",
				message = "must be a supported order status") String status) {
}
