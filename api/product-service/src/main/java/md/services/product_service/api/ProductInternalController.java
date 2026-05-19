package md.services.product_service.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Hidden;
import md.services.product_service.service.ProductContractService;

@Hidden
@RestController
@RequestMapping("/internal/products")
public class ProductInternalController {

	private final ProductContractService productContractService;
	private final String internalServiceToken;

	public ProductInternalController(ProductContractService productContractService,
			@Value("${app.security.internal-service-token}") String internalServiceToken) {
		this.productContractService = productContractService;
		this.internalServiceToken = internalServiceToken;
	}

	@GetMapping("/{productId}")
	public ProductInternalDto getProductForInternalServices(@PathVariable String productId,
			@RequestHeader(value = "X-Internal-Service-Token", required = false) String token) {
		if (!internalServiceToken.equals(token)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Internal product endpoint was not found.");
		}
		return productContractService.getInternalProduct(productId);
	}
}
