package lk.ceylonpick.auth.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Caller details recorded against sign-ins and sessions.
 *
 * <p>The app sits behind Caddy (Architecture §3 CI/CD), so
 * {@code getRemoteAddr()} is the proxy. {@code X-Forwarded-For} is trusted for
 * that reason — which is safe only while nothing but the reverse proxy can
 * reach the app port. Both values are only ever stored hashed (NFR-06).
 */
public final class ClientInfo {

    private ClientInfo() {
    }

    public static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
