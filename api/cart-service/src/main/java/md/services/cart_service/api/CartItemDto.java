package md.services.cart_service.api;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cart item with stable price and product snapshot.")
public record CartItemDto(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int quantity,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal priceAtAdd,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProductSnapshotDto productSnapshot) {
}
