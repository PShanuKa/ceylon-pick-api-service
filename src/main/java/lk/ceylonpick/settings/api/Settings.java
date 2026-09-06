package lk.ceylonpick.settings.api;

import java.math.BigDecimal;

/**
 * Read access to the configurable business rules — the public API other modules
 * depend on.
 *
 * <p>SRS §3: "Business rules are configurable in Admin → Settings unless marked
 * fixed. Changes are logged with actor and time and apply to orders placed
 * after the change." The last clause matters: callers read the value at the
 * moment of the decision and snapshot it onto the order, rather than reading it
 * again later and getting a different answer.
 */
public interface Settings {

    /** BR-02 — the range a vendor may set creator commission within. */
    record CommissionBounds(BigDecimal min, BigDecimal max) {

        public boolean contains(BigDecimal pct) {
            return pct != null && pct.compareTo(min) >= 0 && pct.compareTo(max) <= 0;
        }
    }

    /** BR-20 — "max 25 vendors, 15 SKUs each" in phase 1. */
    record VendorCaps(int maxVendors, int maxSkusPerVendor) {
    }

    CommissionBounds creatorCommissionBounds();

    /**
     * BR-05: "Max COD order total LKR 5,000; per-category caps (Beauty 3,000)."
     *
     * <p>A cart touching several categories takes the lowest cap that applies —
     * one beauty item drags the whole order down to 3,000, which is the reading
     * that keeps the tighter limit meaningful.
     *
     * @param categorySlugs the categories present in the cart
     */
    BigDecimal codCapFor(java.util.Set<String> categorySlugs);

    VendorCaps vendorCaps();

    boolean killSwitchOn();

    BigDecimal decimal(String key);

    int integer(String key);

    boolean flag(String key);

    /** Raw JSON, for callers that need a shape the typed accessors do not cover yet. */
    String json(String key);
}
