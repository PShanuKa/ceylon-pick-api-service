package lk.ceylonpick.auth.api;

/**
 * Admin permission tier.
 *
 * <p>An extension beyond the SRS, which models Admin as one flat role
 * ("Founder/operator role with full access", SRS §2.2). The split follows the
 * admin capability list in UI Spec §8: the actions that move money or change
 * the rules are OWNER-only; the daily operational queues are open to both.
 *
 * <ul>
 *   <li>{@code OWNER} — settings and kill switch, payout approve and mark-paid,
 *       ledger ADJUSTMENT transactions (BR-24), managing admin accounts.
 *   <li>{@code MANAGER} — orders and state overrides, disputes, vendor
 *       verification, creator approval and pause, product moderation,
 *       remittance import; read-only on settings, ledger and payouts.
 * </ul>
 */
public enum AdminRole {
    OWNER,
    MANAGER;

    /** Granted alongside {@code ROLE_ADMIN}, e.g. {@code ADMIN_OWNER}. */
    public String authority() {
        return "ADMIN_" + name();
    }
}
