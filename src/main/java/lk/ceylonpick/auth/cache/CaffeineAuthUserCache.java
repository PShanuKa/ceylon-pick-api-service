package lk.ceylonpick.auth.cache;

import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lk.ceylonpick.auth.AuthProperties;
import lk.ceylonpick.auth.api.AuthUser;

/**
 * In-process {@link AuthUserCache}, bounded by size and by a short write TTL.
 *
 * <p>{@code maximumSize} matters: user ids come from signed tokens, so this is
 * not attacker-controlled, but an unbounded map keyed by identity is still a
 * slow leak on a long-running process.
 */
@Component
public class CaffeineAuthUserCache implements AuthUserCache {

    private final Cache<String, AuthUser> cache;

    public CaffeineAuthUserCache(AuthProperties properties) {
        AuthProperties.Cache config = properties.cache();
        this.cache = Caffeine.newBuilder()
                .maximumSize(config.maxSize())
                .expireAfterWrite(config.ttl())
                .recordStats()
                .build();
    }

    @Override
    public AuthUser get(String userId, Function<String, AuthUser> loader) {
        return cache.get(userId, loader);
    }

    @Override
    public void evict(String userId) {
        cache.invalidate(userId);
    }

    @Override
    public void evictAll() {
        cache.invalidateAll();
    }

    @Override
    public long estimatedSize() {
        return cache.estimatedSize();
    }
}
