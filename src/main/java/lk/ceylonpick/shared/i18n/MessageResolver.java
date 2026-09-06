package lk.ceylonpick.shared.i18n;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Looks up copy in the caller's language.
 *
 * <p>{@link #resolveOrDefault} is what lets error handling stay translatable
 * without touching a single throw site: code throws with a stable code and an
 * English message, and this returns the translation of {@code error.<code>}
 * whenever a bundle has one. Adding Sinhala or Tamil is then a properties-file
 * change, not a code change.
 */
@Component
public class MessageResolver {

    private final MessageSource messages;

    public MessageResolver(MessageSource messages) {
        this.messages = messages;
    }

    /** Returns {@code fallback} unchanged when no bundle defines {@code key}. */
    public String resolveOrDefault(String key, String fallback, Object... args) {
        if (key == null) {
            return fallback;
        }
        return messages.getMessage(key, args, fallback, currentLocale());
    }

    /** For copy that must exist; returns the key itself if it does not, so the gap is visible. */
    public String resolve(String key, Object... args) {
        return messages.getMessage(key, args, key, currentLocale());
    }

    /** The translation of an {@link lk.ceylonpick.shared.web.ApiException} code. */
    public String forErrorCode(String code, String fallback, Object... args) {
        return resolveOrDefault("error." + code, fallback, args);
    }

    public Locale currentLocale() {
        return LocaleContextHolder.getLocale();
    }
}
