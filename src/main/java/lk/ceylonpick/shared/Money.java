package lk.ceylonpick.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * LKR to two decimal places. Architecture §4: the shared kernel owns Money.
 *
 * <p>Always {@link RoundingMode#HALF_EVEN}, because FR-LED-04 names it: a
 * settlement rounds four ways and the parts still have to add up to the order
 * total. Half-up would bias every split in the same direction and leave a drift
 * the reconciliation check (FR-LED-06) would then have to explain.
 *
 * <p>There is no currency field. v1 is LKR only (SRS §7), and a field that only
 * ever holds one value invites code that pretends to handle others.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(SCALE));

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(long rupees) {
        return new Money(BigDecimal.valueOf(rupees));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    /** {@code percentage(6)} on LKR 3,000 is LKR 180.00 — BR-01's platform commission. */
    public Money percentage(BigDecimal percent) {
        return new Money(amount.multiply(percent).divide(BigDecimal.valueOf(100), SCALE, ROUNDING));
    }

    /**
     * Splits this amount by the given percentages and hands whatever rounding
     * left over to the last share.
     *
     * <p>This is the only safe way to divide money. Rounding each share
     * independently can leave the parts a cent short of the whole, which in a
     * double-entry ledger is not a rounding error but an unbalanced transaction
     * the database will reject. FR-LED-04 settles who absorbs it: "rounding
     * absorbed by vendor share".
     *
     * @return one amount per percentage, in order, summing exactly to this amount
     */
    public List<Money> split(List<BigDecimal> percentages) {
        List<Money> parts = new ArrayList<>(percentages.size());
        Money running = ZERO;
        for (int i = 0; i < percentages.size(); i++) {
            if (i == percentages.size() - 1) {
                parts.add(this.minus(running));
            } else {
                Money share = percentage(percentages.get(i));
                parts.add(share);
                running = running.plus(share);
            }
        }
        return parts;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    /** FR-LOC-03: "Money shall display as 'LKR 12,450'". */
    public String display() {
        return "LKR " + amount.setScale(SCALE, ROUNDING).toPlainString();
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
