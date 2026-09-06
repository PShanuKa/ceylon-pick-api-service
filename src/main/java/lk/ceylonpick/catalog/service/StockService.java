package lk.ceylonpick.catalog.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.catalog.domain.StockReservation;
import lk.ceylonpick.catalog.repo.ProductVariantRepository;
import lk.ceylonpick.catalog.repo.StockReservationRepository;
import lk.ceylonpick.shared.Ids;
import lk.ceylonpick.shared.web.ApiException;

/**
 * Holds and releases stock (FR-CAT-04).
 *
 * <p>The whole design rests on one thing: availability is tested inside the
 * UPDATE, not before it. {@code tryReserve} returning 0 rows is the definitive
 * answer that the units are gone, and it stays correct with any number of
 * concurrent checkouts.
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final ProductVariantRepository variants;
    private final StockReservationRepository reservations;
    private final Clock clock;

    public StockService(ProductVariantRepository variants,
                        StockReservationRepository reservations,
                        Clock clock) {
        this.variants = variants;
        this.reservations = reservations;
        this.clock = clock;
    }

    @Transactional
    public StockReservation reserve(String orderId, String variantId, int qty, Duration ttl) {
        if (qty <= 0) {
            throw ApiException.badRequest("INVALID_QTY", "Quantity must be at least 1");
        }
        if (variants.tryReserve(variantId, qty) == 0) {
            throw ApiException.conflict("OUT_OF_STOCK", "That item is no longer available");
        }
        Instant now = clock.instant();
        StockReservation reservation = new StockReservation();
        reservation.setId(Ids.newId());
        reservation.setVariantId(variantId);
        reservation.setOrderId(orderId);
        reservation.setQty(qty);
        reservation.setExpiresAt(now.plus(ttl));
        reservation.setCreatedAt(now);
        return reservations.save(reservation);
    }

    @Transactional
    public int release(String orderId) {
        List<StockReservation> open =
                reservations.findByOrderIdAndReleasedAtIsNullAndConsumedAtIsNull(orderId);
        Instant now = clock.instant();
        for (StockReservation reservation : open) {
            variants.releaseReservation(reservation.getVariantId(), reservation.getQty());
            reservation.setReleasedAt(now);
        }
        reservations.saveAll(open);
        return open.size();
    }

    @Transactional
    public int consume(String orderId) {
        List<StockReservation> open =
                reservations.findByOrderIdAndReleasedAtIsNullAndConsumedAtIsNull(orderId);
        Instant now = clock.instant();
        for (StockReservation reservation : open) {
            if (variants.consumeReservation(reservation.getVariantId(), reservation.getQty()) == 0) {
                // Would mean stock moved underneath a confirmed order. Worth
                // shouting about; the order still stands.
                log.error("Could not consume reservation {} for order {}: variant {} qty {}",
                        reservation.getId(), orderId, reservation.getVariantId(), reservation.getQty());
                continue;
            }
            reservation.setConsumedAt(now);
        }
        reservations.saveAll(open);
        return open.size();
    }

    /**
     * The sweep behind FR-CAT-04's "release it on cancellation or timeout".
     * Cancelling the order itself is the orders module's job; this only frees
     * the stock.
     */
    @Transactional
    public int releaseExpired() {
        Instant now = clock.instant();
        List<StockReservation> expired = reservations.findExpired(now);
        for (StockReservation reservation : expired) {
            variants.releaseReservation(reservation.getVariantId(), reservation.getQty());
            reservation.setReleasedAt(now);
        }
        reservations.saveAll(expired);
        if (!expired.isEmpty()) {
            log.info("Released {} expired stock reservations", expired.size());
        }
        return expired.size();
    }
}
