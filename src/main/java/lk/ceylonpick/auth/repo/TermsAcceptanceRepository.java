package lk.ceylonpick.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.auth.domain.TermsAcceptance;

public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, String> {
}
