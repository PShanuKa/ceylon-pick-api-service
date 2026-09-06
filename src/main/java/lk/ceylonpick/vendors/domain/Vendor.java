package lk.ceylonpick.vendors.domain;

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
import lk.ceylonpick.vendors.api.VendorStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vendor")
@Getter
@Setter
@NoArgsConstructor
public class Vendor {

    public enum RtoFeePolicy {
        FLAT, SPLIT, NONE
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    /** Null until the application is approved; applicants have no account. */
    @Column(name = "user_id")
    private String userId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "maker_name", nullable = false)
    private String makerName;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "district", nullable = false)
    private String district;

    @Column(name = "story")
    private String story;

    @Column(name = "story_video_key")
    private String storyVideoKey;

    @Column(name = "photo_key")
    private String photoKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pickup_address")
    private Map<String, Object> pickupAddress;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VendorStatus status = VendorStatus.APPLIED;

    /** BR-23: three strikes trigger a review. */
    @Column(name = "strikes", nullable = false)
    private int strikes;

    @Enumerated(EnumType.STRING)
    @Column(name = "rto_fee_policy", nullable = false)
    private RtoFeePolicy rtoFeePolicy = RtoFeePolicy.FLAT;

    /** NFR-06: AES-GCM ciphertext, key held outside the database. */
    @Column(name = "bank_details_enc")
    private byte[] bankDetailsEnc;

    @Column(name = "bank_details_masked")
    private String bankDetailsMasked;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
