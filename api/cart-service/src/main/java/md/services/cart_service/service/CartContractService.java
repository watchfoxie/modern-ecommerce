package md.services.cart_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import md.services.cart_service.api.AddCartItemRequest;
import md.services.cart_service.api.CartDto;
import md.services.cart_service.api.CartItemDto;
import md.services.cart_service.api.ProductSnapshotDto;
import md.services.cart_service.api.UpdateCartItemRequest;
import md.services.cart_service.client.ProductInternalClient;
import md.services.cart_service.client.ProductInternalClient.ProductInternalDto;
import md.services.cart_service.domain.CartDocument;
import md.services.cart_service.domain.CartDocument.CartItem;
import md.services.cart_service.domain.CartDocument.ProductSnapshot;
import md.services.cart_service.repository.CartRepository;

@Service
public class CartContractService {

	private final CartRepository cartRepository;
	private final ProductInternalClient productInternalClient;

	public CartContractService(CartRepository cartRepository, ProductInternalClient productInternalClient) {
		this.cartRepository = cartRepository;
		this.productInternalClient = productInternalClient;
	}

	public CartDto getCurrentCart() {
		return toDto(loadOrCreateCart(currentUserId()));
	}

	public CartDto addItem(AddCartItemRequest request) {
		ProductInternalDto product = loadActiveProduct(request.productId());
		if (product.stock() != null && product.stock() < request.quantity()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Product stock is lower than requested quantity.");
		}

		CartDocument cart = loadOrCreateCart(currentUserId());
		List<CartItem> items = new ArrayList<>(cart.items());
		int existingIndex = findItemIndex(items, request.productId());
		CartItem canonicalItem = canonicalItem(product, existingIndex >= 0
				? items.get(existingIndex).quantity() + request.quantity()
				: request.quantity());
		if (existingIndex >= 0) {
			items.set(existingIndex, canonicalItem);
		} else {
			items.add(canonicalItem);
		}
		return toDto(save(cart, items));
	}

	public CartDto updateItem(String productId, UpdateCartItemRequest request) {
		ProductInternalDto product = loadActiveProduct(productId);
		if (product.stock() != null && product.stock() < request.quantity()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Product stock is lower than requested quantity.");
		}

		CartDocument cart = loadExistingCart(currentUserId());
		List<CartItem> items = new ArrayList<>(cart.items());
		int existingIndex = findItemIndex(items, productId);
		if (existingIndex < 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item was not found.");
		}
		items.set(existingIndex, canonicalItem(product, request.quantity()));
		return toDto(save(cart, items));
	}

	public void deleteItem(String productId) {
		CartDocument cart = loadExistingCart(currentUserId());
		List<CartItem> items = cart.items().stream()
				.filter(item -> !Objects.equals(item.productId(), productId))
				.toList();
		save(cart, items);
	}

	public void clearCart() {
		cartRepository.findByUserId(currentUserId()).ifPresent(cartRepository::delete);
	}

	private CartDocument loadOrCreateCart(String userId) {
		return cartRepository.findByUserId(userId)
				.orElseGet(() -> {
					Instant now = Instant.now();
					return cartRepository.save(new CartDocument(null, userId, List.of(), now, now));
				});
	}

	private CartDocument loadExistingCart(String userId) {
		return cartRepository.findByUserId(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart was not found."));
	}

	private CartDocument save(CartDocument cart, List<CartItem> items) {
		return cartRepository.save(new CartDocument(cart.id(), cart.userId(), List.copyOf(items), cart.createdAt(), Instant.now()));
	}

	private ProductInternalDto loadActiveProduct(String productId) {
		ProductInternalDto product = productInternalClient.getProduct(productId);
		if (product == null || Boolean.FALSE.equals(product.isActive()) || product.effectivePrice() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product is not available.");
		}
		return product;
	}

	private CartItem canonicalItem(ProductInternalDto product, int quantity) {
		return new CartItem(
				product.id(),
				quantity,
				product.effectivePrice(),
				new ProductSnapshot(product.name(), product.primaryImageUrl(), product.categorySlug()));
	}

	private int findItemIndex(List<CartItem> items, String productId) {
		for (int index = 0; index < items.size(); index++) {
			if (Objects.equals(items.get(index).productId(), productId)) {
				return index;
			}
		}
		return -1;
	}

	private CartDto toDto(CartDocument cart) {
		return new CartDto(
				cart.id(),
				cart.userId(),
				cart.items().stream().map(this::toDto).toList(),
				toOffsetDateTime(cart.createdAt()),
				toOffsetDateTime(cart.updatedAt()));
	}

	private CartItemDto toDto(CartItem item) {
		ProductSnapshot snapshot = item.productSnapshot();
		return new CartItemDto(
				item.productId(),
				item.quantity(),
				item.priceAtAdd(),
				new ProductSnapshotDto(snapshot.name(), snapshot.imageUrl(), snapshot.categorySlug()));
	}

	private OffsetDateTime toOffsetDateTime(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private String currentUserId() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes instanceof ServletRequestAttributes servletAttributes) {
			String headerUserId = servletAttributes.getRequest().getHeader("X-User-Id");
			if (headerUserId != null && !headerUserId.isBlank()) {
				return headerUserId;
			}
			if (servletAttributes.getRequest().getUserPrincipal() != null) {
				return servletAttributes.getRequest().getUserPrincipal().getName();
			}
		}
		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user identity.");
	}
}
