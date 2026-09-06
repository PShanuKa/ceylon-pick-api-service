package lk.ceylonpick.orders.domain;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lk.ceylonpick.orders.api.ActorType;
import lk.ceylonpick.orders.api.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FR-ORD-10: "Every state change shall be recorded in an order status history
 * with from, to, actor, reason and timestamp."
 *
 * <p>Two very different readers depend on this one table. The buyer's tracking
 * page renders it as a timeline (FR-ORD-12), and the admin order detail reads it
 * as the audit of who did what and why (FR-ADM-02) — which is what makes BR-25's
 * "record a reason" worth anything.
 *
 * <p>Append-only by convention: rows are inserted, never updated. Rewriting
 * history would defeat the point of keeping it.
 */
@Entity
@Table(name = "order_status_history")
@Getter
@Setter
@NoArgsConstructor
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private String orderId;

    /** Null on the first row, where the order came into existence at PLACED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private OrderStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false)
    private ActorType actorType;

    /** The user id where there is one; null for SYSTEM, COURIER and GATEWAY. */
    @Column(name = "actor_id", updatable = false)
    private String actorId;

    /** Mandatory for an admin override (BR-25); optional otherwise. */
    @Column(name = "reason", updatable = false)
    private String reason;

    /** Whatever the transition needs remembered: a tracking number, an IPN reference, a courier's raw status. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", updatable = false)
    private Map<String, Object> meta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static OrderStatusHistory of(String orderId, OrderStatus from, OrderStatus to,
                                        ActorType actorType, String actorId, String reason,
                                        Map<String, Object> meta, Instant at) {
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.orderId = orderId;
        entry.fromStatus = from;
        entry.toStatus = to;
        entry.actorType = actorType;
        entry.actorId = actorId;
        entry.reason = reason;
        entry.meta = meta;
        entry.createdAt = at;
        return entry;
    }
}
