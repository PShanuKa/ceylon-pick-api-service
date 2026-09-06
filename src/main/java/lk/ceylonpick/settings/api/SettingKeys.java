package lk.ceylonpick.settings.api;

/**
 * The keys seeded by {@code V2__catalog_vendors.sql}, each tied to the business
 * rule it configures. Referencing a constant rather than a literal is what keeps
 * a renamed key from turning into a silent default at runtime.
 */
public final class SettingKeys {

    /** BR-01 — platform commission, % of product price. */
    public static final String PLATFORM_COMMISSION_PCT = "platform_commission_pct";
    /** BR-02 — the range a vendor may set creator commission within. */
    public static final String CREATOR_COMMISSION_BOUNDS = "creator_commission_bounds";
    /** BR-05 — maximum COD order total, LKR. */
    public static final String COD_CAP_GLOBAL = "cod_cap_global";
    /** BR-05 — per-category COD caps, LKR. */
    public static final String COD_CAP_BY_CATEGORY = "cod_cap_by_category";
    /** BR-07 / FR-NOT-01 — OTP limits. Auth reads these from its own config today. */
    public static final String OTP_RULES = "otp_rules";
    /** BR-08 — minutes before an unpaid prepaid order is cancelled. */
    public static final String PREPAID_TIMEOUT_MIN = "prepaid_timeout_min";
    /** BR-10 — days between DELIVERED and SETTLED. */
    public static final String SETTLEMENT_HOLD_DAYS = "settlement_hold_days";
    /** BR-11 — hours a buyer has to open a dispute. */
    public static final String DISPUTE_WINDOW_HOURS = "dispute_window_hours";
    /** BR-14 — creator payout days and minimum. */
    public static final String CREATOR_PAYOUT = "creator_payout";
    /** BR-15 — vendor payout weekday and cutoff. */
    public static final String VENDOR_PAYOUT = "vendor_payout";
    /** BR-16 — creator auto-pause and removal thresholds. */
    public static final String CREATOR_RTO_THRESHOLDS = "creator_rto_thresholds";
    /** BR-17 — attribution cookie lifetime, days. */
    public static final String ATTRIBUTION_COOKIE_DAYS = "attribution_cookie_days";
    /** BR-20 — maximum vendors, and maximum SKUs per vendor. */
    public static final String VENDOR_CAPS = "vendor_caps";
    /** BR-22 — RTO fee, LKR. Marked "to confirm" in the SRS. */
    public static final String RTO_FEE_LKR = "rto_fee_lkr";
    /** BR-23 — strikes before a vendor is reviewed. */
    public static final String VENDOR_STRIKE_LIMIT = "vendor_strike_limit";
    /** FR-ORD-14 — disables new orders while tracking stays up. */
    public static final String KILL_SWITCH_NEW_ORDERS = "kill_switch_new_orders";
    public static final String OCCASION_BANNER = "occasion_banner";

    private SettingKeys() {
    }
}
