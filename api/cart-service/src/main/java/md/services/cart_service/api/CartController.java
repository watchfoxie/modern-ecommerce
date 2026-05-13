package md.services.cart_service.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import md.services.cart_service.service.CartContractService;

@Validated
@RestController
@RequestMapping("/carts/me")
@Tag(name = "Carts")
public class CartController {

	private final CartContractService cartContractService;

	public CartController(CartContractService cartContractService) {
		this.cartContractService = cartContractService;
	}

	@GetMapping
	@Operation(summary = "Get the authenticated user's persistent cart")
	@ApiResponse(responseCode = "200", description = "Cart returned")
	public CartDto getCurrentCart() {
		return cartContractService.getCurrentCart();
	}

	@PostMapping("/items")
	@Operation(summary = "Add an item to the authenticated user's cart")
	public ResponseEntity<CartDto> addItem(@Valid @RequestBody AddCartItemRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(cartContractService.addItem(request));
	}

	@PutMapping("/items/{productId}")
	@Operation(summary = "Replace the quantity of a cart item")
	public CartDto updateItem(@PathVariable @NotBlank String productId,
			@Valid @RequestBody UpdateCartItemRequest request) {
		return cartContractService.updateItem(productId, request);
	}

	@DeleteMapping("/items/{productId}")
	@Operation(summary = "Remove an item from the authenticated user's cart")
	public ResponseEntity<Void> deleteItem(@PathVariable @NotBlank String productId) {
		cartContractService.deleteItem(productId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	@Operation(summary = "Clear the authenticated user's cart")
	public ResponseEntity<Void> clearCart() {
		cartContractService.clearCart();
		return ResponseEntity.noContent().build();
	}

}
