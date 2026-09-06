package lk.ceylonpick.auth.api;

/**
 * Creates the login for someone an admin has just approved.
 *
 * <p>Vendors and creators apply as visitors with no account (FR-VEN-01,
 * FR-CRE-01); the login only exists once the application is approved. Those
 * modules call this rather than writing to {@code app_user} themselves, which
 * keeps the credential rules — password policy, lockout, session invalidation —
 * in one place.
 */
public interface StaffAccounts {

    /**
     * Creates an account with no usable password and sends a set-password link,
     * so a credential is never chosen on the new person's behalf.
     *
     * @return the new {@code app_user} id
     */
    String provision(Role role, String email, String phone);
}
