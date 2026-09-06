package lk.ceylonpick.catalog.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import lk.ceylonpick.catalog.api.ProductStatus;
import lk.ceylonpick.catalog.domain.Product;

public interface ProductRepository extends JpaRepository<Product, String> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Product> findByVendorId(String vendorId, Pageable pageable);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    long countByVendorId(String vendorId);

    /**
     * FR-CAT-01: "Category page lists only LIVE products of VERIFIED vendors."
     * The vendor set comes from {@code VendorApi}, not a join — catalog does not
     * read the vendors module's tables (Architecture §4).
     */
    @Query("""
            select p from Product p
             where p.status = lk.ceylonpick.catalog.api.ProductStatus.LIVE
               and p.vendorId in :vendorIds
               and (:categoryId is null or p.categoryId = :categoryId)
            """)
    Page<Product> findLive(@Param("vendorIds") Collection<String> vendorIds,
                           @Param("categoryId") String categoryId,
                           Pageable pageable);

    /**
     * FR-CAT-05, a "Should" for v1: keyword search over titles and descriptions.
     * The JSONB copy is cast to text so one LIKE covers every language at once;
     * revisit with a proper index if it stops meeting the 500 ms target.
     */
    @Query(value = """
            select * from product p
             where p.status = 'LIVE'
               and p.vendor_id in (:vendorIds)
               and (lower(p.title::text) like :term or lower(coalesce(p.description::text,'')) like :term)
            """,
            countQuery = """
            select count(*) from product p
             where p.status = 'LIVE'
               and p.vendor_id in (:vendorIds)
               and (lower(p.title::text) like :term or lower(coalesce(p.description::text,'')) like :term)
            """,
            nativeQuery = true)
    Page<Product> search(@Param("vendorIds") Collection<String> vendorIds,
                         @Param("term") String lowercaseLikeTerm,
                         Pageable pageable);

    List<Product> findByVendorIdAndStatus(String vendorId, ProductStatus status);
}
