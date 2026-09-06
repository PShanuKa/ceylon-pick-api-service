package lk.ceylonpick.auth.api;

/**
 * The OTP that confirms a COD order (BR-07, FR-ORD-03).
 *
 * <p>The {@code otp_challenge} table belongs to auth, which already enforces the
 * expiry, the three attempts, the three resends and the per-phone hourly cap.
 * Orders asks for a challenge and asks whether a code was right; it does not get
 * to reimplement any of those limits.
 */
public interface OrderOtp {

    /** The challenge, and its plain code when dev is configured to echo it. */
    record IssuedOrderOtp(String challengeId, String maskedPhone, String devCode) {
    }

    /** Sends a code for this order. Subject to BR-07 and the FR-NOT-01 hourly cap. */
    IssuedOrderOtp issue(String orderId, String phone);

    /**
     * Checks a submitted code and burns the challenge.
     *
     * <p>Throws on a wrong, expired or over-attempted code — the caller does not
     * decide what "too many" means.
     */
    void confirm(String challengeId, String code, String orderId);

    /** BR-07: at most three resends per order. */
    IssuedOrderOtp resend(String challengeId);
}
