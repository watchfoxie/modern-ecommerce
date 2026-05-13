package md.services.product_service.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import md.services.product_service.domain.ProductDocument;

public interface ProductRepository extends MongoRepository<ProductDocument, String> {

	Optional<ProductDocument> findBySlugAndIsActiveTrue(String slug);

	Optional<ProductDocument> findBySlug(String slug);

	boolean existsBySlug(String slug);

	boolean existsBySlugAndIdNot(String slug, String id);

}
