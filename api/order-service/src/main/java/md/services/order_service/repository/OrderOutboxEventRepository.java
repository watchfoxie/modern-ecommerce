package md.services.order_service.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import md.services.order_service.domain.OrderOutboxEventDocument;

public interface OrderOutboxEventRepository extends MongoRepository<OrderOutboxEventDocument, String> {

	List<OrderOutboxEventDocument> findTop25ByStatusOrderByCreatedAtAsc(String status);

	boolean existsByEventTypeAndAggregateId(String eventType, String aggregateId);
}
