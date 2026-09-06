package lk.ceylonpick.catalog.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import lk.ceylonpick.catalog.domain.ProductVariant;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {

    List<ProductVariant> findByProductIdOrderBySku(String productId);

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);

    /** BR-20: SKUs are capped per vendor, not per product. */
    @Query("""
            select count(v) from ProductVariant v
             where v.productId in (select p.id from Product p where p.vendorId = :vendorId)
            """)
    long countByVendor(@Param("vendorId") String vendorId);

    /**
     * FR-CAT-04: "Two concurrent orders for the last unit: exactly one succeeds."
     *
     * <p>One statement, with the availability test in the WHERE clause. Reading
     * the stock and then writing it back would let two transactions both see the
     * last unit and both reserve it; here the second update simply matches no
     * rows and returns 0. The {@code reserved_qty <= stock_qty} check constraint
     * is the backstop if this is ever bypassed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ProductVariant v
               set v.reservedQty = v.reservedQty + :qty
             where v.id = :variantId
               and v.active = true
               and v.reservedQty + :qty <= v.stockQty
            """)
    int tryReserve(@Param("variantId") String variantId, @Param("qty") int qty);

    /** Puts held stock back after a cancellation or a timeout. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ProductVariant v
               set v.reservedQty = v.reservedQty - :qty
             where v.id = :variantId and v.reservedQty >= :qty
            """)
    int releaseReservation(@Param("variantId") String variantId, @Param("qty") int qty);

    /** A reservation that became a sale: the held units leave stock for good. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ProductVariant v
               set v.reservedQty = v.reservedQty - :qty,
                   v.stockQty = v.stockQty - :qty
             where v.id = :variantId and v.reservedQty >= :qty and v.stockQty >= :qty
            """)
    int consumeReservation(@Param("variantId") String variantId, @Param("qty") int qty);
}
