package lk.ceylonpick.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.auth.domain.BuyerProfile;

public interface BuyerProfileRepository extends JpaRepository<BuyerProfile, String> {
}
