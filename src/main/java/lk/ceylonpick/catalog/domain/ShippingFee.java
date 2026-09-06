package lk.ceylonpick.catalog.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * BR-04, keyed by district. The product page needs it because FR-CAT-03 puts the
 * delivery estimate in front of the buy action, not at checkout.
 */
@Entity
@Table(name = "shipping_fee")
@Getter
@Setter
@NoArgsConstructor
public class ShippingFee {

    @Id
    @Column(name = "district", nullable = false, updatable = false)
    private String district;

    @Column(name = "fee", nullable = false)
    private BigDecimal fee;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "eta_days", nullable = false)
    private int etaDays = 2;
}
