package lk.ceylonpick.auth.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.auth.domain.EmailVerificationToken;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, String> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
}
