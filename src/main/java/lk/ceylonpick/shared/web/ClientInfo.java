package lk.ceylonpick.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Caller details recorded against sign-ins, sessions and audit entries.
 *
 * <p>The app sits behind Caddy (Architecture §3), so {@code getRemoteAddr()} is
 * the proxy. {@code X-Forwarded-For} is trusted for that reason — which holds
 * only while nothing but the reverse proxy can reach the app port. Both values
 * are stored hashed, never raw (NFR-06).
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
