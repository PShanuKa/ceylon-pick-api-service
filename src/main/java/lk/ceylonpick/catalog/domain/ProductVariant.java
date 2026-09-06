package lk.ceylonpick.catalog.domain;

import java.math.BigDecimal;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A sellable SKU.
 *
 * <p>{@code stockQty} and {@code reservedQty} are the pair that decides whether
 * an order can be placed. The database holds {@code reserved_qty <= stock_qty},
 * so a second concurrent reservation of the last unit is rejected by the check
 * constraint even if two transactions read the same starting value — which is
 * what FR-CAT-04's "exactly one succeeds" needs.
 */
@Entity
@Table(name = "product_variant")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private String productId;

    @Column(name = "sku", nullable = false)
    private String sku;

    /** e.g. {@code {"size":"250g"}} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", nullable = false)
    private Map<String, String> attrs = Map.of();

    @Column(name = "price_override")
    private BigDecimal priceOverride;

    @Column(name = "stock_qty", nullable = false)
    private int stockQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public int availableQty() {
        return stockQty - reservedQty;
    }

    public BigDecimal effectivePrice(BigDecimal basePrice) {
        return priceOverride != null ? priceOverride : basePrice;
    }
}
