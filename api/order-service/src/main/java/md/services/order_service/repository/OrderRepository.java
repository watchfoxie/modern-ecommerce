package md.services.order_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import md.services.order_service.domain.OrderDocument;

public interface OrderRepository extends MongoRepository<OrderDocument, String> {

	Page<OrderDocument> findByUserId(String userId, Pageable pageable);

	Page<OrderDocument> findByStatus(String status, Pageable pageable);
}
