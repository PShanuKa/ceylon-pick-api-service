package lk.ceylonpick.auth.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.ceylonpick.auth.api.AuthUser;
import lk.ceylonpick.auth.service.AuthCookies;
import lk.ceylonpick.auth.service.AuthUserService;
import lk.ceylonpick.auth.service.TokenService;

/**
 * Turns the access-token cookie into an {@link org.springframework.security.core.Authentication}.
 *
 * <p>The order matters. The signature and expiry are checked first, so an
 * unsigned or forged token never reaches the cache or the database. Only then
 * is the subject used to look up the live {@link AuthUser} — which is why a
 * role change, a lock or a disable takes effect on the very next request rather
 * than whenever the token would have expired.
 *
 * <p>An unusable token leaves the context anonymous rather than failing here;
 * the entry point turns that into a 401 only if the endpoint actually required
 * authentication.
 */
@Component
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtCookieAuthenticationFilter.class);

    private final AuthCookies cookies;
    private final TokenService tokens;
    private final AuthUserService authUsers;

    public JwtCookieAuthenticationFilter(AuthCookies cookies, TokenService tokens, AuthUserService authUsers) {
        this.cookies = cookies;
        this.tokens = tokens;
        this.authUsers = authUsers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        Jwt jwt = tokens.decodeAccessToken(cookies.readAccess(request));
        if (jwt == null) {
            return;
        }
        AuthUser user = authUsers.find(jwt.getSubject()).orElse(null);
        if (user == null) {
            log.debug("Access token for unknown user {}", jwt.getSubject());
            return;
        }
        if (!user.status().canAuthenticate()) {
            log.debug("Rejected request for {} account {}", user.status(), user.userId());
            return;
        }
        if (user.invalidatesTokenIssuedAt(jwt.getIssuedAt())) {
            // Signed out everywhere, password changed, or force-logged-out by an
            // admin — all after this token was minted.
            log.debug("Access token for {} predates session invalidation", user.userId());
            return;
        }

        PreAuthenticatedAuthenticationToken authentication =
                new PreAuthenticatedAuthenticationToken(user, null, user.authorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
