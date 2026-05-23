package md.services.category_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import md.services.category_service.domain.CategoryDocument;

public interface CategoryRepository extends MongoRepository<CategoryDocument, String> {

	List<CategoryDocument> findByIsActiveTrueOrderByDisplayOrderAscNameAsc();

	List<CategoryDocument> findByParentIdAndIsActiveTrueOrderByDisplayOrderAscNameAsc(String parentId);

	Page<CategoryDocument> findByIsActiveTrue(Pageable pageable);

	Page<CategoryDocument> findByParentIdAndIsActiveTrue(String parentId, Pageable pageable);

	Optional<CategoryDocument> findBySlugAndIsActiveTrue(String slug);

	Optional<CategoryDocument> findBySlug(String slug);

	boolean existsBySlug(String slug);

	boolean existsBySlugAndIdNot(String slug, String id);

}
