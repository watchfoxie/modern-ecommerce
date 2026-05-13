package md.services.auth_service.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import md.services.auth_service.domain.AuthUserDocument;

public interface AuthUserRepository extends MongoRepository<AuthUserDocument, String> {

	Optional<AuthUserDocument> findByEmailIgnoreCase(String email);

	Optional<AuthUserDocument> findByPasswordResetToken(String passwordResetToken);

	boolean existsByEmailIgnoreCase(String email);
}
