package lk.ceylonpick.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalisesToTwoDecimalPlaces() {
        assertThat(Money.of("100").amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(Money.of("100.005").amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(Money.of(1450).amount()).isEqualTo(new BigDecimal("1450.00"));
    }

    /** Half-even, not half-up: 0.125 rounds down to the even cent, 0.135 rounds up. */
    @Test
    void roundsHalfToEven() {
        assertThat(Money.of("0.125").amount()).isEqualTo(new BigDecimal("0.12"));
        assertThat(Money.of("0.135").amount()).isEqualTo(new BigDecimal("0.14"));
    }

    @Test
    void addsSubtractsAndMultiplies() {
        assertThat(Money.of("1450.00").plus(Money.of("300.00"))).isEqualTo(Money.of("1750.00"));
        assertThat(Money.of("1750.00").minus(Money.of("300.00"))).isEqualTo(Money.of("1450.00"));
        assertThat(Money.of("1450.00").times(3)).isEqualTo(Money.of("4350.00"));
    }

    /** BR-01: 6% platform commission on the worked example's LKR 3,000. */
    @Test
    void takesAPercentage() {
        assertThat(Money.of(3000).percentage(new BigDecimal("6"))).isEqualTo(Money.of("180.00"));
        assertThat(Money.of(3000).percentage(new BigDecimal("15"))).isEqualTo(Money.of("450.00"));
    }

    /**
     * The property that matters: a split always adds back up to the whole. An
     * unbalanced transaction is rejected at commit by the ledger trigger, so a
     * lost cent here is a failed settlement, not a cosmetic error.
     */
    @Test
    void splitAlwaysSumsBackToTheWhole() {
        Money total = Money.of("3000.00");
        List<Money> parts = total.split(List.of(
                new BigDecimal("15"),   // creator, BR-02
                new BigDecimal("6")));  // platform, BR-01; vendor takes the rest

        assertThat(parts).containsExactly(Money.of("450.00"), Money.of("2550.00"));
        assertThat(parts.stream().reduce(Money.ZERO, Money::plus)).isEqualTo(total);
    }

    /** An amount that cannot divide cleanly still has to reconcile exactly. */
    @Test
    void splitAbsorbsRoundingInTheLastShare() {
        Money total = Money.of("1000.01");
        List<Money> parts = total.split(List.of(
                new BigDecimal("33.333"), new BigDecimal("33.333"), new BigDecimal("33.334")));

        assertThat(parts.stream().reduce(Money.ZERO, Money::plus))
                .as("the parts reconstruct the total exactly")
                .isEqualTo(total);
    }

    @Test
    void splitHandlesAwkwardAmountsWithoutDrift() {
        for (int cents = 1; cents <= 200; cents++) {
            Money total = Money.of(new BigDecimal(cents).movePointLeft(2));
            List<Money> parts = total.split(List.of(new BigDecimal("15"), new BigDecimal("6")));
            assertThat(parts.stream().reduce(Money.ZERO, Money::plus))
                    .as("total %s reconstructs", total)
                    .isEqualTo(total);
        }
    }

    @Test
    void comparesAndReportsSign() {
        assertThat(Money.of("1.00").isGreaterThan(Money.of("0.99"))).isTrue();
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.of("-1.00").isNegative()).isTrue();
        assertThat(Money.of("1.00").isPositive()).isTrue();
    }

    /** FR-LOC-03. */
    @Test
    void displaysWithTheCurrencyPrefix() {
        assertThat(Money.of(12450).display()).isEqualTo("LKR 12450.00");
    }

    @Test
    void equalityIgnoresHowTheAmountWasWritten() {
        assertThat(Money.of("100")).isEqualTo(Money.of("100.00"));
        assertThat(Money.of("100").hashCode()).isEqualTo(Money.of("100.000").hashCode());
    }
}
