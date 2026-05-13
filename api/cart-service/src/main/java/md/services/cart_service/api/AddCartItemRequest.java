package md.services.cart_service.api;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to add a product to the authenticated user's cart.")
public record AddCartItemRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String productId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(1) int quantity,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0.00") BigDecimal priceAtAdd,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid ProductSnapshotDto productSnapshot) {
}
