package lk.ceylonpick.shared.i18n;

import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * English, Sinhala and Tamil (SRS §7: {@code language IN ('en','si','ta')}).
 *
 * <p>NFR-06 requires the privacy notice in all three, and BR message templates
 * are stored per language, so every operator-visible and buyer-visible string
 * needs to be translatable rather than a literal in Java.
 */
@Configuration
public class I18nConfig {

    public static final Locale ENGLISH = Locale.of("en");
    public static final Locale SINHALA = Locale.of("si");
    public static final Locale TAMIL = Locale.of("ta");

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding("UTF-8");
        // A key missing from messages_si falls back to messages.properties
        // (English) rather than to whatever locale the server happens to run in,
        // so a partly translated bundle degrades predictably.
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(ENGLISH);
        return source;
    }

    /**
     * Locale comes from {@code Accept-Language}, narrowed to the three we
     * support.
     *
     * <p>A signed-in person also has {@code app_user.language}. Preferring that
     * over the header needs the authenticated principal, so it belongs in a
     * filter inside the auth module — not here, where it would invert the
     * dependency.
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(ENGLISH, SINHALA, TAMIL));
        resolver.setDefaultLocale(ENGLISH);
        return resolver;
    }
}
