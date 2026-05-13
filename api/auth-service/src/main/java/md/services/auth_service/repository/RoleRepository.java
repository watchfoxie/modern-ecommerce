package md.services.auth_service.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import md.services.auth_service.domain.RoleDocument;

public interface RoleRepository extends MongoRepository<RoleDocument, String> {

	Optional<RoleDocument> findByName(String name);

	List<RoleDocument> findByIdIn(Collection<String> ids);
}
