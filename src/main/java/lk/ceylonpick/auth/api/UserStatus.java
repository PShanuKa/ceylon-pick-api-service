package lk.ceylonpick.auth.api;

public enum UserStatus {
    /** Normal. May still be temporarily barred by {@code locked_until}. */
    ACTIVE,
    /** Administratively locked. Distinct from the automatic failed-login lockout. */
    LOCKED,
    /** Deactivated; cannot sign in and existing sessions are rejected. */
    DISABLED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
