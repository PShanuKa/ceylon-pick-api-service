package lk.ceylonpick.auth.service;

import org.springframework.stereotype.Component;

import lk.ceylonpick.auth.AuthProperties;
import lk.ceylonpick.shared.web.ApiException;

/** FR-AUTH-02: "password policy 10 chars". */
@Component
public class PasswordPolicy {

    private final int minLength;

    public PasswordPolicy(AuthProperties properties) {
        this.minLength = properties.password().minLength();
    }

    public void validate(String password, String email) {
        if (password == null || password.length() < minLength) {
            // The length is passed as an argument so the Sinhala and Tamil
            // messages carry the real number too.
            throw ApiException.badRequest("WEAK_PASSWORD",
                    "Password must be at least " + minLength + " characters", minLength);
        }
        if (email != null && password.equalsIgnoreCase(email)) {
            throw ApiException.badRequest("WEAK_PASSWORD_IS_EMAIL",
                    "Password must not be your email address");
        }
    }

    public int minLength() {
        return minLength;
    }
}
