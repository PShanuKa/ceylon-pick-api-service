package lk.ceylonpick.vendors.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.vendors.domain.VendorVerification;

public interface VendorVerificationRepository extends JpaRepository<VendorVerification, String> {
}
