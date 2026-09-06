package lk.ceylonpick.vendors.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.vendors.api.VendorStatus;
import lk.ceylonpick.vendors.domain.Vendor;

public interface VendorRepository extends JpaRepository<Vendor, String> {

    Optional<Vendor> findBySlug(String slug);

    Optional<Vendor> findByUserId(String userId);

    boolean existsBySlug(String slug);

    Page<Vendor> findByStatus(VendorStatus status, Pageable pageable);

    Page<Vendor> findByStatusIn(List<VendorStatus> statuses, Pageable pageable);

    /** BR-20: at most 25 vendors in phase 1. Counts everyone who can or could sell. */
    long countByStatusIn(List<VendorStatus> statuses);

    @org.springframework.data.jpa.repository.Query("select v.id from Vendor v where v.status = :status")
    List<String> findIdsByStatus(@org.springframework.data.repository.query.Param("status") VendorStatus status);
}
