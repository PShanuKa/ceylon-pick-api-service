package lk.ceylonpick.auth.api;

/** Architecture §3: "roles BUYER/CREATOR/VENDOR/ADMIN". */
public enum Role {
    ADMIN,
    CREATOR,
    VENDOR,
    BUYER;

    /** The Spring Security authority for this role, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }

    /** Staff sign in with email and password; buyers may, but need not. */
    public boolean isStaff() {
        return this != BUYER;
    }
}
