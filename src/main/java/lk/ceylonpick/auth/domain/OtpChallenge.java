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

/**
 * BR-07: 6 digits, 10-minute expiry, max 3 attempts, max 3 resends. Only the
 * salted hash of the code is stored (Architecture §5).
 */
@Entity
@Table(name = "otp_challenge")
@Getter
@Setter
@NoArgsConstructor
public class OtpChallenge {

    public enum Purpose {
        /** COD order confirmation (BR-07). Owned by the orders module. */
        ORDER_CONFIRM,
        /** Passwordless buyer sign-in (FR-AUTH-01). */
        BUYER_LOGIN,
        /** Mandatory admin second factor (FR-AUTH-02). */
        ADMIN_LOGIN,
        /** Optional creator/vendor second factor (FR-AUTH-02). */
        LOGIN_2FA,
        /** Step-up before a bank-detail edit (FR-AUTH-04). */
        BANK_EDIT,
        PHONE_VERIFY
    }

    public enum Channel {
        SMS, WHATSAPP
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private Purpose purpose;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "salt", nullable = false)
    private String salt;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel = Channel.SMS;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "resends", nullable = false)
    private int resends;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** Set once the confirmed challenge has been exchanged, so it cannot be replayed. */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    /** Open = still awaiting a correct code. */
    public boolean isOpen(Instant now) {
        return !isConfirmed() && !isConsumed() && !isExpired(now);
    }
}
