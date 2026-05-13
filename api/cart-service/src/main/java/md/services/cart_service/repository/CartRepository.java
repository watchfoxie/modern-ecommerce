package md.services.cart_service.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import md.services.cart_service.domain.CartDocument;

public interface CartRepository extends MongoRepository<CartDocument, String> {

	Optional<CartDocument> findByUserId(String userId);
}
