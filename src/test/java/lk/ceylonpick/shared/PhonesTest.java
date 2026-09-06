package lk.ceylonpick.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhonesTest {

    /**
     * Every accepted spelling must collapse to one string, or a buyer who types
     * their number differently at checkout gets a second identity — and BR-18's
     * creator-phone comparison silently stops matching.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "0771234567",
            "+94771234567",
            "94771234567",
            "771234567",
            "077 123 4567",
            "077-123-4567",
            " +94 77 123 4567 "
    })
    void normalisesEverySpellingToTheSameCanonicalForm(String input) {
        assertThat(Phones.normalise(input)).isEqualTo("+94771234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",
            "0112345678",      // landline, not a mobile
            "07712345678",     // one digit too many
            "077123456",       // one digit too few
            "+441234567890",
            "not a number"
    })
    void rejectsAnythingThatIsNotASriLankanMobile(String input) {
        assertThat(Phones.normalise(input)).isNull();
        assertThat(Phones.isValid(input)).isFalse();
    }

    @Test
    void treatsNullAndBlankAsInvalid() {
        assertThat(Phones.normalise(null)).isNull();
        assertThat(Phones.normalise("   ")).isNull();
    }

    @Test
    void masksAllButTheLastThreeDigits() {
        assertThat(Phones.mask("+94771234567")).isEqualTo("+94 ***** 567");
    }
}
