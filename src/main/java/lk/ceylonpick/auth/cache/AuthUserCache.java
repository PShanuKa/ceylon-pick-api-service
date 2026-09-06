package lk.ceylonpick.auth.cache;

import java.util.function.Function;

import lk.ceylonpick.auth.api.AuthUser;

/**
 * Holds the authorization snapshot read on every authenticated request.
 *
 * <p>Behind an interface on purpose. ADR-2 rules out Redis for v1 ("Add Redis
 * only at 2+ app instances or > 1,000 orders/day"), so the implementation is
 * in-process — but when a second instance appears, only the implementation and
 * its bean change, not the authentication filter.
 *
 * <p>The in-process implementation is correct only because every write to a
 * user's role or status calls {@link #evict}. That happens in the service
 * layer, never in a controller, so background jobs and admin actions are
 * covered alike. The configured TTL is a safety net for anything that still
 * slips through, not the primary mechanism.
 */
public interface AuthUserCache {

    /**
     * Returns the cached snapshot, loading it through {@code loader} on a miss.
     * A {@code null} from the loader is not cached.
     */
    AuthUser get(String userId, Function<String, AuthUser> loader);

    /** Drop one user, so the next request re-reads them from the database. */
    void evict(String userId);

    /** Drop everything. Used by tests and by an admin-triggered flush. */
    void evictAll();

    long estimatedSize();
}
