package lk.ceylonpick.orders.api;

/**
 * SRS §2: COD preference is 52% and rising, so it is a design constraint rather
 * than a phase. Which one an order uses decides its whole early lifecycle —
 * AWAITING_OTP or AWAITING_PAYMENT — and how the money reaches us.
 */
public enum PayMethod {
    /** Cash on delivery. Confirmed by OTP (BR-07), capped by BR-05, collected by the courier. */
    COD,
    /** PayHere. Confirmed only by a verified IPN (FR-ORD-04). */
    PREPAID;

    public boolean isCod() {
        return this == COD;
    }
}
