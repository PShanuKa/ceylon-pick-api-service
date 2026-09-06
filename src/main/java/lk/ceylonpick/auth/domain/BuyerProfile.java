package lk.ceylonpick.auth.domain;

import java.time.Instant;
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

/** SRS §7 buyer data. One saved address, not an address book (UI Spec §5). */
@Entity
@Table(name = "buyer_profile")
@Getter
@Setter
@NoArgsConstructor
public class BuyerProfile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(name = "full_name")
    private String fullName;

    /** {@code {line, city, district, landmark}} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "saved_address")
    private Map<String, Object> savedAddress;

    @Column(name = "district")
    private String district;

    @Column(name = "whatsapp_optin", nullable = false)
    private boolean whatsappOptin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
