package lk.ceylonpick.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class HashesTest {

    @Test
    void saltChangesTheDigestForTheSameCode() {
        String a = Hashes.sha256("salt-one", "123456");
        String b = Hashes.sha256("salt-two", "123456");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void sameSaltAndCodeAlwaysAgree() {
        assertThat(Hashes.sha256("s", "123456")).isEqualTo(Hashes.sha256("s", "123456"));
    }

    @Test
    void matchesRejectsNulls() {
        assertThat(Hashes.matches(null, "x")).isFalse();
        assertThat(Hashes.matches("x", null)).isFalse();
        assertThat(Hashes.matches("x", "x")).isTrue();
    }

    @Test
    void numericCodeHasTheRequestedLengthAndOnlyDigits() {
        for (int i = 0; i < 200; i++) {
            String code = Hashes.randomNumericCode(6);
            assertThat(code).hasSize(6).containsOnlyDigits();
        }
    }

    /** A guessable code would make the 3-attempt limit meaningless. */
    @Test
    void numericCodesAreNotRepetitive() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(Hashes.randomNumericCode(6));
        }
        assertThat(seen).hasSizeGreaterThan(450);
    }

    @Test
    void tokensAreUrlSafeAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String token = Hashes.randomToken(32);
            assertThat(token).matches("[A-Za-z0-9_-]+");
            seen.add(token);
        }
        assertThat(seen).hasSize(500);
    }

    @Test
    void ipHashHidesTheAddressButStaysStable() {
        String hashed = Hashes.ipHash("203.0.113.7");
        assertThat(hashed).isNotNull().doesNotContain("203.0.113.7");
        assertThat(hashed).isEqualTo(Hashes.ipHash("203.0.113.7"));
        assertThat(Hashes.ipHash(null)).isNull();
    }
}
