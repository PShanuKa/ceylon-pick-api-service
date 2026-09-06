package lk.ceylonpick.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards the two ways a translation bundle rots: a key spelled differently from
 * the English one (which then silently falls back and looks untranslated), and
 * a key that only exists in a translation (which no code path can ever reach).
 */
class MessageBundleTest {

    private static Properties load(String name) {
        Properties properties = new Properties();
        try (InputStream in = MessageBundleTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as("bundle %s is on the classpath", name).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + name, e);
        }
        return properties;
    }

    @ParameterizedTest
    @ValueSource(strings = {"messages_si.properties", "messages_ta.properties"})
    void translationDefinesNoKeyEnglishLacks(String bundle) {
        Set<String> english = load("messages.properties").stringPropertyNames();
        Set<String> orphans = new TreeSet<>(load(bundle).stringPropertyNames());
        orphans.removeAll(english);
        assertThat(orphans)
                .as("%s has keys missing from messages.properties (a typo, or copy nothing reads)", bundle)
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"messages_si.properties", "messages_ta.properties"})
    void translationHasNoBlankValues(String bundle) {
        Properties properties = load(bundle);
        Set<String> blank = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (properties.getProperty(key).isBlank()) {
                blank.add(key);
            }
        }
        // A blank value wins over the English fallback and shows the user nothing.
        assertThat(blank).as("%s has blank values; remove the key instead", bundle).isEmpty();
    }

    @Test
    void everyErrorCodeUsedByTheApiHasEnglishCopy() {
        Set<String> keys = load("messages.properties").stringPropertyNames();
        assertThat(keys).contains(
                "error.UNAUTHENTICATED", "error.FORBIDDEN", "error.VALIDATION_FAILED",
                "error.INVALID_CREDENTIALS", "error.ACCOUNT_LOCKED", "error.ACCOUNT_UNAVAILABLE",
                "error.INVALID_PHONE", "error.INVALID_EMAIL", "error.WEAK_PASSWORD",
                "error.OTP_INVALID", "error.OTP_EXPIRED", "error.OTP_RATE_LIMITED",
                "error.SESSION_REVOKED", "error.SESSION_EXPIRED", "error.INVALID_SESSION");
    }

    /** The minimum-length placeholder has to survive into every language. */
    @ParameterizedTest
    @ValueSource(strings = {"messages.properties", "messages_si.properties", "messages_ta.properties"})
    void weakPasswordKeepsItsPlaceholder(String bundle) {
        String value = load(bundle).getProperty("error.WEAK_PASSWORD");
        if (value != null) {
            assertThat(value).as("%s must interpolate the minimum length", bundle).contains("{0}");
        }
    }
}
