package lk.ceylonpick.orders.api;

/**
 * How a creator came to be credited for an order (BR-17).
 *
 * <p>"30-day cookie, last click wins; code entered at checkout overrides cookie;
 * frozen at PLACED." Recording which of the two it was matters for the creator's
 * own reporting: a typed code is a deliberate recommendation, a cookie is a
 * click that may have been weeks ago.
 */
public enum AttributionType {
    /** Arrived through a creator's link or storefront; resolved from the cookie. */
    LINK,
    /** The buyer typed the creator's code at checkout, which overrides any cookie. */
    CODE
}
