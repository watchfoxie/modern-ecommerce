package md.services.user_service.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import md.services.user_service.domain.UserProfileDocument;

public interface UserProfileRepository extends MongoRepository<UserProfileDocument, String> {

	Optional<UserProfileDocument> findByAuthId(String authId);

	Optional<UserProfileDocument> findByEmailIgnoreCase(String email);
}
