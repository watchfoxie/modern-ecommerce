package md.services.order_service.api;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import md.services.order_service.service.OrderContractService;

@Validated
@RestController
@RequestMapping("/orders")
@Tag(name = "Orders")
public class OrderCommandController {

	private final OrderContractService orderContractService;

	public OrderCommandController(OrderContractService orderContractService) {
		this.orderContractService = orderContractService;
	}

	@PostMapping
	@Operation(summary = "Create an order from the authenticated user's cart")
	@ApiResponse(responseCode = "202", description = "Order command accepted")
	public ResponseEntity<OrderAcceptedResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
		return ResponseEntity.accepted().body(orderContractService.createOrder(request));
	}

	@GetMapping
	@Operation(summary = "List the authenticated user's order history")
	public PagedResponseDto<OrderDto> listCurrentUserOrders(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) int size,
			@RequestParam(defaultValue = "createdAt") String sort,
			@RequestParam(defaultValue = "desc") String direction) {
		return orderContractService.listCurrentUserOrders(page, size, sort, direction);
	}

	@GetMapping("/{orderId}")
	@Operation(summary = "Get one order for the authenticated user")
	public OrderDto getCurrentUserOrder(@PathVariable @NotBlank String orderId) {
		return orderContractService.getCurrentUserOrder(orderId);
	}

	@GetMapping("/all")
	@Operation(summary = "List all orders for administration")
	public PagedResponseDto<OrderDto> listAllOrders(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) int size,
			@RequestParam(required = false) String status) {
		return orderContractService.listAllOrders(page, size, status);
	}

	@PatchMapping("/{orderId}/status")
	@Operation(summary = "Update an order status for administration")
	public OrderDto updateStatus(@PathVariable @NotBlank String orderId,
			@Valid @RequestBody OrderStatusUpdateRequest request) {
		return orderContractService.updateStatus(orderId, request);
	}

}
