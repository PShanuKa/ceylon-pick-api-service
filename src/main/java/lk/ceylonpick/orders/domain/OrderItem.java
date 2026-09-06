package lk.ceylonpick.orders.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.Generated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lk.ceylonpick.shared.Money;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of an order, and a snapshot of the terms it was sold on.
 *
 * <p>{@code unitPrice}, {@code creatorPct} and {@code platformPct} are copies,
 * not references. SRS §3: settings "apply to orders placed after the change", so
 * an order settles months later on the numbers it was placed with — a vendor who
 * raises a price, or an admin who changes the platform rate, cannot reach
 * backwards into money already owed.
 *
 * <p>{@code titleSnapshot} exists for the same reason in a smaller way: the
 * printed slip and the buyer's statement should say what they bought, even if
 * the product has since been renamed or archived.
 */
@Entity
@Table(name = "order_item")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private String orderId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private String variantId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private String productId;

    /** Denormalised from the product, because BR-19 makes it the same for every line. */
    @Column(name = "vendor_id", nullable = false, updatable = false)
    private String vendorId;

    @Column(name = "title_snapshot", nullable = false, updatable = false)
    private String titleSnapshot;

    @Column(name = "qty", nullable = false, updatable = false)
    private int qty;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private Money unitPrice;

    /** BR-02, snapshot: what this creator earns on this line. */
    @Column(name = "creator_pct", nullable = false, updatable = false)
    private BigDecimal creatorPct;

    /** BR-01, snapshot: the platform's cut. */
    @Column(name = "platform_pct", nullable = false, updatable = false)
    private BigDecimal platformPct;

    /**
     * {@code GENERATED ALWAYS AS (qty * unit_price) STORED}. The database owns
     * it, so a line total can never disagree with its own parts; Hibernate reads
     * the value back after each write rather than computing its own.
     */
    @Generated
    @Column(name = "line_total", insertable = false, updatable = false)
    private Money lineTotal;

    /** The same arithmetic, for code that has the item in hand before it is flushed. */
    public Money computeLineTotal() {
        return unitPrice.times(qty);
    }
}
