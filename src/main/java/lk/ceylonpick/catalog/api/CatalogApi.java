package lk.ceylonpick.catalog.api;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * The catalog module's public API (Architecture §4).
 *
 * <p>The stock methods are the contract orders depends on. Reservation is keyed
 * by order id so release and consume can act on a whole basket at once, which is
 * what cancellation and settlement actually need.
 */
public interface CatalogApi {

    /** What orders needs to price a line and decide COD eligibility. */
    record VariantPricing(
            String variantId,
            String productId,
            String vendorId,
            String categoryId,
            String titleSnapshot,
            BigDecimal unitPrice,
            BigDecimal creatorPct,
            boolean codAllowed,
            boolean prepaidOnly,
            boolean sellable) {
    }

    Optional<VariantPricing> variantPricing(String variantId);

    /**
     * Holds {@code qty} of a variant for an order.
     *
     * <p>FR-CAT-04: 15 minutes for prepaid, 24 hours for COD. Throws rather than
     * returning false, because a caller that ignores the result would oversell.
     */
    void reserveStock(String orderId, String variantId, int qty, Duration ttl);

    /** Returns every open reservation for an order to available stock. */
    void releaseStock(String orderId);

    /** Turns an order's reservations into a sale; the units leave stock for good. */
    void consumeStock(String orderId);

    /** BR-04, by district. Empty when we do not deliver there. */
    Optional<BigDecimal> shippingFee(String district);
}
