package lk.ceylonpick.auth.service;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lk.ceylonpick.auth.AuthProperties;

/**
 * Builds the session cookies.
 *
 * <p>Architecture §3 and NFR-05 fix the shape: httpOnly, SameSite, HTTPS only.
 * httpOnly is what keeps the tokens out of reach of injected script, which is
 * also why CSRF protection is required alongside — see {@code SecurityConfig}.
 *
 * <p>The refresh cookie is scoped to the auth endpoints, so it is not attached
 * to ordinary API calls and cannot leak from them.
 */
@Component
public class AuthCookies {

    private final AuthProperties.Cookie config;

    public AuthCookies(AuthProperties properties) {
        this.config = properties.cookie();
    }

    public ResponseCookie access(String token, Duration ttl) {
        return base(config.accessName(), token, config.path(), ttl);
    }

    public ResponseCookie refresh(String token, Duration ttl) {
        return base(config.refreshName(), token, config.refreshPath(), ttl);
    }

    public ResponseCookie clearAccess() {
        return base(config.accessName(), "", config.path(), Duration.ZERO);
    }

    public ResponseCookie clearRefresh() {
        return base(config.refreshName(), "", config.refreshPath(), Duration.ZERO);
    }

    public String readAccess(HttpServletRequest request) {
        return read(request, config.accessName());
    }

    public String readRefresh(HttpServletRequest request) {
        return read(request, config.refreshName());
    }

    private ResponseCookie base(String name, String value, String path, Duration ttl) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(config.secure())
                .sameSite(config.sameSite())
                .path(path)
                .maxAge(ttl)
                .build();
    }

    private static String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
