package md.services.order_service.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Checkout request. User identity, totals, and canonical prices are resolved server-side.")
public record CreateOrderRequest(
		@NotNull @Valid DeliveryAddressRequest deliveryAddress,
		@NotNull @Valid PaymentRequest payment,
		@Size(max = 500) String notes) {

	@Schema(description = "Delivery address snapshot captured at checkout.")
	public record DeliveryAddressRequest(
			@NotBlank @Size(max = 160) String street,
			@NotBlank @Size(max = 80) String city,
			@NotBlank @Size(max = 80) String district,
			@Size(max = 20) String postalCode,
			@NotBlank @Size(max = 160) String recipientName,
			@NotBlank @Size(max = 30)
			@Pattern(regexp = "^[+0-9()\\-\\s]*$", message = "must be a phone-like value") String recipientPhone) {
	}

	@Schema(description = "Payment intent selected by the customer.")
	public record PaymentRequest(
			@NotBlank @Pattern(regexp = "CARD|CASH", message = "must be CARD or CASH") String method,
			String transactionId) {
	}

	@Schema(description = "Optional client-side cart selection hint. Canonical cart data is loaded server-side.")
	public record CartSelectionHint(
			@NotBlank String productId,
			int quantity) {
	}

	@SuppressWarnings("unused")
	private static void cartSelectionHintIsDocumentedOnly(List<CartSelectionHint> ignored) {
	}
}
