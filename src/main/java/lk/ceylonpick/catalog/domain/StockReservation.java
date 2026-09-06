package lk.ceylonpick.catalog.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FR-CAT-04: stock is held while an order is being paid for or confirmed —
 * 15 minutes for prepaid, 24 hours for COD — and released on cancellation or
 * timeout.
 *
 * <p>Consumed and released are separate: a reservation that becomes a real sale
 * is consumed, one that expires is released. Keeping them apart means the sweep
 * job can never claw back stock that has already shipped.
 */
@Entity
@Table(name = "stock_reservation")
@Getter
@Setter
@NoArgsConstructor
public class StockReservation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private String variantId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private String orderId;

    @Column(name = "qty", nullable = false, updatable = false)
    private int qty;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isOpen() {
        return releasedAt == null && consumedAt == null;
    }
}
