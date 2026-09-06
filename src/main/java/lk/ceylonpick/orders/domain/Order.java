package lk.ceylonpick.orders.domain;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lk.ceylonpick.orders.api.AttributionType;
import lk.ceylonpick.orders.api.OrderStatus;
import lk.ceylonpick.orders.api.PayMethod;
import lk.ceylonpick.shared.Money;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One order, for one vendor (BR-19).
 *
 * <p>The table is named {@code "order"} — a reserved word — so every query has
 * to quote it. That is the reference schema's choice and later migrations depend
 * on it, so it stays.
 *
 * <p>{@link #stampTransitionTo} is the only way status moves, and it is called
 * by {@code OrderService} after {@link OrderStateMachine} has approved the edge
 * — never on its own. Keeping the timestamps next to the status change is what
 * stops a DELIVERED order without a {@code delivered_at}, which the settlement
 * job (FR-ORD-07) would then never pick up.
 */
@Entity
@Table(name = "\"order\"")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    /** Human-facing, e.g. {@code CP-10231}. What a buyer quotes on WhatsApp. */
    @Column(name = "number", nullable = false, updatable = false)
    private String number;

    @Column(name = "vendor_id", nullable = false, updatable = false)
    private String vendorId;

    /** Null for a guest checkout: the phone is the identity (Architecture §11.1). */
    @Column(name = "buyer_user_id")
    private String buyerUserId;

    @Column(name = "buyer_phone", nullable = false)
    private String buyerPhone;

    @Column(name = "buyer_name", nullable = false)
    private String buyerName;

    /** {@code {line, city, district, landmark}} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "address", nullable = false)
    private Map<String, Object> address;

    @Column(name = "district", nullable = false)
    private String district;

    /** The buyer's language, so every message about this order is in it (FR-NOT-02). */
    @Column(name = "language", nullable = false)
    private String language = "en";

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_method", nullable = false, updatable = false)
    private PayMethod payMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "subtotal", nullable = false)
    private Money subtotal;

    @Column(name = "shipping_fee", nullable = false)
    private Money shippingFee = Money.ZERO;

    /** The database enforces {@code total = subtotal + shipping_fee}; see {@link #recalculateTotal}. */
    @Column(name = "total", nullable = false)
    private Money total;

    /**
     * BR-17: frozen at PLACED. A later click cannot move commission from one
     * creator to another after the order exists.
     */
    @Column(name = "creator_id")
    private String creatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribution_type")
    private AttributionType attributionType;

    /** BR-18: buyer phone equals the creator's, so the order is stored unattributed and flagged. */
    @Column(name = "self_referral_flag", nullable = false)
    private boolean selfReferralFlag;

    /** BR-09: no photo, no PACKED. */
    @Column(name = "packing_photo_key")
    private String packingPhotoKey;

    @Column(name = "cancel_reason")
    private String cancelReason;

    /**
     * Optimistic lock. Two admins working the same order in the queue, or a
     * courier webhook landing while someone edits, must not silently overwrite
     * each other — the second write fails and is retried against fresh state.
     */
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "packed_at")
    private Instant packedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    /** Starts both the BR-10 settlement hold and the BR-11 dispute window. */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    /** When the order reached a terminal state, whichever one. */
    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    /** Keeps the CHECK constraint satisfied whenever either part changes. */
    public void recalculateTotal() {
        this.total = subtotal.plus(shippingFee);
    }

    /** True once the order has a creator to pay, which self-referral removes (BR-18). */
    public boolean isAttributed() {
        return creatorId != null && !selfReferralFlag;
    }

    /** Records the timestamp that goes with a state, so the timeline reads correctly. */
    public void stampTransitionTo(OrderStatus to, Instant at) {
        switch (to) {
            case CONFIRMED -> confirmedAt = at;
            case PACKED -> packedAt = at;
            case SHIPPED -> shippedAt = at;
            case DELIVERED -> deliveredAt = at;
            case SETTLED -> settledAt = at;
            default -> {
                // The remaining states carry no dedicated column.
            }
        }
        if (to.isTerminal()) {
            closedAt = at;
        }
        this.status = to;
    }
}
