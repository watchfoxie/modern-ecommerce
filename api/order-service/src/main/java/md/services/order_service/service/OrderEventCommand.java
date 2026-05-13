package md.services.order_service.service;

import java.math.BigDecimal;

public record OrderEventCommand(
		String orderId,
		String userId,
		String customerEmail,
		BigDecimal totalAmount,
		String currency) {
}
