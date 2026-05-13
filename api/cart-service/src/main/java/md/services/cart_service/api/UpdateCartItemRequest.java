package md.services.cart_service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "Request to replace the quantity of a cart item.")
public record UpdateCartItemRequest(
		@Min(1) int quantity) {
}
