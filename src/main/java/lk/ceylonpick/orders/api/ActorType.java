package lk.ceylonpick.orders.api;

/**
 * Who caused a transition. Recorded on every row of {@code order_status_history}
 * (FR-ORD-10), which is what makes the timeline and the admin audit answerable.
 */
public enum ActorType {
    BUYER,
    VENDOR,
    /** May trigger any transition in the table, but always with a reason (BR-25). */
    ADMIN,
    /** Scheduled jobs: OTP expiry, prepaid timeout, settlement. */
    SYSTEM,
    /** A courier webhook, poll or CSV. */
    COURIER,
    /** PayHere, via a verified IPN. */
    GATEWAY
}
