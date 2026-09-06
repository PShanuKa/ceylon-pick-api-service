package lk.ceylonpick.settings.domain;

import java.time.Instant;

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
 * One configurable business rule.
 *
 * <p>The value is raw JSON because the rules are not one shape: some are
 * numbers ({@code platform_commission_pct}), some booleans
 * ({@code kill_switch_new_orders}), some objects ({@code vendor_caps}). It is
 * held as a string and parsed by the typed accessors on
 * {@link lk.ceylonpick.settings.api.Settings}, so a caller asking for the wrong
 * shape fails at the read rather than silently.
 */
@Entity
@Table(name = "setting")
@Getter
@Setter
@NoArgsConstructor
public class Setting {

    @Id
    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "description")
    private String description;

    /** SRS §3: "Changes are logged with actor and time." */
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
