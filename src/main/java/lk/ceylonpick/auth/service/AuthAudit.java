package lk.ceylonpick.auth.service;

/** The {@code action} values this module writes to the audit log. */
public final class AuthAudit {

    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String OTP_ISSUED = "OTP_ISSUED";
    public static final String OTP_FAILED = "OTP_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String SESSIONS_INVALIDATED = "SESSIONS_INVALIDATED";
    public static final String REFRESH_REUSE_DETECTED = "REFRESH_REUSE_DETECTED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String ADMIN_CREATED = "ADMIN_CREATED";
    public static final String ADMIN_ROLE_CHANGED = "ADMIN_ROLE_CHANGED";
    public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";

    private AuthAudit() {
    }
}
