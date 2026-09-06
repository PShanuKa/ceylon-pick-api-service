package lk.ceylonpick.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FR-AUTH-05: acceptance recorded with version and timestamp. Append-only. */
@Entity
@Table(name = "terms_acceptance")
@Getter
@Setter
@NoArgsConstructor
public class TermsAcceptance {

    public enum TermsType {
        CREATOR, VENDOR, BUYER_PRIVACY, BUYER_TERMS
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false)
    private TermsType termsType;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "ip_hash")
    private String ipHash;
}
