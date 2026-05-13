package md.services.category_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import md.services.category_service.domain.CategoryDocument;

public interface CategoryRepository extends MongoRepository<CategoryDocument, String> {

	List<CategoryDocument> findByIsActiveTrueOrderByDisplayOrderAscNameAsc();

	List<CategoryDocument> findByParentIdAndIsActiveTrueOrderByDisplayOrderAscNameAsc(String parentId);

	Optional<CategoryDocument> findBySlugAndIsActiveTrue(String slug);

	Optional<CategoryDocument> findBySlug(String slug);

	boolean existsBySlug(String slug);

	boolean existsBySlugAndIdNot(String slug, String id);

}
