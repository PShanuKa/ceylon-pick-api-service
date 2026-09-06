package lk.ceylonpick.auth.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.auth.domain.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
