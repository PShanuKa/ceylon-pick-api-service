package lk.ceylonpick.auth.service;

import org.springframework.stereotype.Component;

import lk.ceylonpick.auth.AuthProperties;
import lk.ceylonpick.auth.web.AuthException;

/** FR-AUTH-02: "password policy 10 chars". */
@Component
public class PasswordPolicy {

    private final int minLength;

    public PasswordPolicy(AuthProperties properties) {
        this.minLength = properties.password().minLength();
    }

    public void validate(String password, String email) {
        if (password == null || password.length() < minLength) {
            throw AuthException.badRequest("WEAK_PASSWORD",
                    "Password must be at least " + minLength + " characters");
        }
        if (email != null && password.equalsIgnoreCase(email)) {
            throw AuthException.badRequest("WEAK_PASSWORD", "Password must not be your email address");
        }
    }

    public int minLength() {
        return minLength;
    }
}
