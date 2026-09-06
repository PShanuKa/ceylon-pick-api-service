package lk.ceylonpick.orders.api;

/**
 * Architecture §6: "Order status is the spine of the system: it decides when
 * stock is reserved, when a courier is called, when money moves and when a
 * creator gets paid."
 *
 * <p>The buyer never sees these names. UI Spec §9 maps them to friendly labels
 * ("On its way"); the internal names appear only in admin.
 */
public enum OrderStatus {

    /** Checkout accepted, stock reserved, attribution frozen (BR-17). */
    PLACED,
    /** Prepaid: a PayHere intent exists. Only a verified IPN moves this on (FR-ORD-04). */
    AWAITING_PAYMENT,
    /** COD: an OTP has been sent. BR-07 governs attempts, resends and the 24-hour auto-cancel. */
    AWAITING_OTP,
    /** Paid or OTP-confirmed. The vendor now owes a parcel. */
    CONFIRMED,
    /** Packed with a photo (BR-09). */
    PACKED,
    /** Handed to a courier with a tracking number. */
    SHIPPED,
    /** Delivered; the BR-10 hold and the BR-11 dispute window both start here. */
    DELIVERED,
    /** Money split in the ledger (FR-LED-04). Terminal. */
    SETTLED,
    /** A buyer raised a problem inside 48 hours; settlement is paused (FR-ORD-08). */
    DISPUTED,
    /** Returned to the vendor. Terminal. */
    RTO,
    /** Never confirmed, or cancelled before dispatch. Terminal. */
    CANCELLED,
    /** Prepaid money returned. Terminal. */
    REFUNDED;

    /** Architecture §6: "Terminal states: SETTLED, CANCELLED, RTO, REFUNDED." */
    public boolean isTerminal() {
        return this == SETTLED || this == CANCELLED || this == RTO || this == REFUNDED;
    }

    /** True once the buyer owes money and the vendor owes goods. */
    public boolean isConfirmedOrLater() {
        return switch (this) {
            case CONFIRMED, PACKED, SHIPPED, DELIVERED, SETTLED, DISPUTED, RTO, REFUNDED -> true;
            case PLACED, AWAITING_PAYMENT, AWAITING_OTP, CANCELLED -> false;
        };
    }

    /** While true, the order still holds reserved stock that a cancellation must release. */
    public boolean holdsStockReservation() {
        return this == PLACED || this == AWAITING_PAYMENT || this == AWAITING_OTP;
    }
}
