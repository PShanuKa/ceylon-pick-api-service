package lk.ceylonpick.catalog.domain;

import java.math.BigDecimal;
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
import lk.ceylonpick.catalog.api.ProductStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "vendor_id", nullable = false, updatable = false)
    private String vendorId;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(name = "slug", nullable = false)
    private String slug;

    /** Per-language copy (FR-LOC-02); the UI shows the best available. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "title", nullable = false)
    private Map<String, String> title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description")
    private Map<String, String> description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ingredients")
    private Map<String, String> ingredients;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    /** BR-02: the vendor sets this within bounds read from settings. */
    @Column(name = "creator_pct", nullable = false)
    private BigDecimal creatorPct;

    @Column(name = "cod_allowed", nullable = false)
    private boolean codAllowed = true;

    /** BR-06: made-to-order and personalised goods are prepaid only. */
    @Column(name = "prepaid_only", nullable = false)
    private boolean prepaidOnly;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "moderation_note")
    private String moderationNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    /**
     * The database rejects prepaid-only and COD-allowed together, so keep the
     * pair consistent whenever either is set.
     */
    public void setPrepaidOnly(boolean prepaidOnly) {
        this.prepaidOnly = prepaidOnly;
        if (prepaidOnly) {
            this.codAllowed = false;
        }
    }
}
