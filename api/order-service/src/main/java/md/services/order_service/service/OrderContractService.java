package md.services.order_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import md.services.order_service.api.CreateOrderRequest.DeliveryAddressRequest;
import md.services.order_service.api.CreateOrderRequest;
import md.services.order_service.api.OrderAcceptedResponse;
import md.services.order_service.api.OrderDto;
import md.services.order_service.api.OrderDto.DeliveryAddressDto;
import md.services.order_service.api.OrderDto.OrderItemDto;
import md.services.order_service.api.OrderDto.PaymentDto;
import md.services.order_service.api.OrderStatusUpdateRequest;
import md.services.order_service.api.PagedResponseDto;
import md.services.order_service.client.CartInternalClient;
import md.services.order_service.client.CartInternalClient.CartDto;
import md.services.order_service.client.ProductInternalClient;
import md.services.order_service.client.ProductInternalClient.ProductInternalDto;
import md.services.order_service.domain.OrderDocument;
import md.services.order_service.domain.OrderDocument.DeliveryAddress;
import md.services.order_service.domain.OrderDocument.OrderItem;
import md.services.order_service.domain.OrderDocument.Payment;
import md.services.order_service.repository.OrderRepository;

@Service
public class OrderContractService {

	private static final String STATUS_CREATED = "CREATED";
	private static final String PAYMENT_PENDING = "PENDING";
	private static final String DEFAULT_CURRENCY = "MDL";
	private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "updatedAt", "orderNumber", "status", "totalAmount");
	private static final Set<String> ALLOWED_STATUSES = Set.of("CREATED", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED");

	private final OrderRepository orderRepository;
	private final CartInternalClient cartInternalClient;
	private final ProductInternalClient productInternalClient;
	private final OrderOutboxService orderOutboxService;

	public OrderContractService(OrderRepository orderRepository, CartInternalClient cartInternalClient,
			ProductInternalClient productInternalClient, OrderOutboxService orderOutboxService) {
		this.orderRepository = orderRepository;
		this.cartInternalClient = cartInternalClient;
		this.productInternalClient = productInternalClient;
		this.orderOutboxService = orderOutboxService;
	}

	public OrderAcceptedResponse createOrder(CreateOrderRequest request) {
		String userId = currentUserId();
		CartDto cart = cartInternalClient.getCurrentCart(userId);
		if (cart == null || cart.items() == null || cart.items().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty.");
		}

		List<ValidatedOrderItem> validatedItems = cart.items().stream()
				.map(this::validatedOrderItem)
				.toList();
		List<OrderItem> items = validatedItems.stream().map(ValidatedOrderItem::item).toList();
		String currency = validatedItems.getFirst().currency();
		BigDecimal totalAmount = items.stream()
				.map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		Instant now = Instant.now();
		OrderDocument order = orderRepository.save(new OrderDocument(
				null,
				userId,
				currentUserEmail(),
				generateOrderNumber(now),
				items,
				toDocument(request.deliveryAddress()),
				new Payment(request.payment().method(), PAYMENT_PENDING, request.payment().transactionId()),
				STATUS_CREATED,
				totalAmount,
				currency,
				request.notes(),
				now,
				now));

		orderOutboxService.enqueueAndDispatchOrderCreated(new OrderEventCommand(
				order.id(),
				order.userId(),
				order.customerEmail(),
				order.totalAmount(),
				order.currency()));

		return new OrderAcceptedResponse("ACCEPTED", order.id(), order.orderNumber(), "Order command accepted.");
	}

	public PagedResponseDto<OrderDto> listCurrentUserOrders(int page, int size, String sort, String direction) {
		return toPagedResponse(orderRepository.findByUserId(currentUserId(), pageable(page, size, sort, direction)));
	}

	public OrderDto getCurrentUserOrder(String orderId) {
		OrderDocument order = loadOrder(orderId);
		if (!Objects.equals(order.userId(), currentUserId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order was not found.");
		}
		return toDto(order);
	}

	public PagedResponseDto<OrderDto> listAllOrders(int page, int size, String status) {
		requireAdmin();
		Pageable pageable = pageable(page, size, "createdAt", "desc");
		if (status == null || status.isBlank()) {
			return toPagedResponse(orderRepository.findAll(pageable));
		}
		return toPagedResponse(orderRepository.findByStatus(normalizeStatus(status), pageable));
	}

	public OrderDto updateStatus(String orderId, OrderStatusUpdateRequest request) {
		requireAdmin();
		OrderDocument order = loadOrder(orderId);
		String status = normalizeStatus(request.status());
		OrderDocument updated = orderRepository.save(new OrderDocument(
				order.id(),
				order.userId(),
				order.customerEmail(),
				order.orderNumber(),
				order.items(),
				order.deliveryAddress(),
				order.payment(),
				status,
				order.totalAmount(),
				order.currency(),
				order.notes(),
				order.createdAt(),
				Instant.now()));
		return toDto(updated);
	}

	private ValidatedOrderItem validatedOrderItem(CartInternalClient.CartItemDto cartItem) {
		String productId = cartItem.productId();
		int quantity = cartItem.quantity();
		ProductInternalDto product = productInternalClient.getProduct(productId);
		if (product == null || Boolean.FALSE.equals(product.isActive()) || product.effectivePrice() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product is not available.");
		}
		if (product.stock() != null && product.stock() < quantity) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Product stock is lower than cart quantity.");
		}
		if (cartItem.priceAtAdd() == null || cartItem.priceAtAdd().compareTo(product.effectivePrice()) != 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart price is no longer current.");
		}
		return new ValidatedOrderItem(new OrderItem(
				product.id(),
				product.name(),
				product.brand(),
				product.primaryImageUrl(),
				quantity,
				product.effectivePrice()),
				product.currency() == null || product.currency().isBlank() ? DEFAULT_CURRENCY : product.currency());
	}

	private DeliveryAddress toDocument(DeliveryAddressRequest request) {
		return new DeliveryAddress(
				request.street(),
				request.city(),
				request.district(),
				request.postalCode(),
				request.recipientName(),
				request.recipientPhone());
	}

	private OrderDocument loadOrder(String orderId) {
		return orderRepository.findById(orderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order was not found."));
	}

	private Pageable pageable(int page, int size, String sort, String direction) {
		String sortProperty = ALLOWED_SORTS.contains(sort) ? sort : "createdAt";
		Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
		return PageRequest.of(page, size, Sort.by(sortDirection, sortProperty));
	}

	private String normalizeStatus(String status) {
		String normalized = status.toUpperCase(Locale.ROOT);
		if (!ALLOWED_STATUSES.contains(normalized)) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Order status is not supported.");
		}
		return normalized;
	}

	private String generateOrderNumber(Instant now) {
		return "ORD-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
				.withZone(ZoneOffset.UTC)
				.format(now)
				+ "-" + Long.toUnsignedString(Math.abs(System.nanoTime()), 36).toUpperCase(Locale.ROOT);
	}

	private PagedResponseDto<OrderDto> toPagedResponse(Page<OrderDocument> page) {
		return new PagedResponseDto<>(
				page.getContent().stream().map(this::toDto).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast());
	}

	private OrderDto toDto(OrderDocument order) {
		return new OrderDto(
				order.id(),
				order.userId(),
				order.orderNumber(),
				order.items().stream().map(this::toDto).toList(),
				toDto(order.deliveryAddress()),
				toDto(order.payment()),
				order.status(),
				order.totalAmount(),
				order.currency(),
				order.notes(),
				toOffsetDateTime(order.createdAt()),
				toOffsetDateTime(order.updatedAt()));
	}

	private OrderItemDto toDto(OrderItem item) {
		return new OrderItemDto(
				item.productId(),
				item.name(),
				item.brand(),
				item.imageUrl(),
				item.quantity(),
				item.unitPrice());
	}

	private DeliveryAddressDto toDto(DeliveryAddress address) {
		return new DeliveryAddressDto(
				address.street(),
				address.city(),
				address.district(),
				address.postalCode(),
				address.recipientName(),
				address.recipientPhone());
	}

	private PaymentDto toDto(Payment payment) {
		return new PaymentDto(payment.method(), payment.status(), payment.transactionId());
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
		}
		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user identity.");
	}

	private String currentUserEmail() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes instanceof ServletRequestAttributes servletAttributes) {
			String headerEmail = servletAttributes.getRequest().getHeader("X-User-Email");
			if (headerEmail != null && !headerEmail.isBlank()) {
				return headerEmail;
			}
		}
		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user email.");
	}

	private void requireAdmin() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.noneMatch("ROLE_ADMIN"::equals)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role is required.");
		}
	}

	private record ValidatedOrderItem(OrderItem item, String currency) {
	}
}
