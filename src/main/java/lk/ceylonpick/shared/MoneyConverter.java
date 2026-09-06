package lk.ceylonpick.shared;

import java.math.BigDecimal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link Money} in a {@code NUMERIC(12,2)} column.
 *
 * <p>Applied automatically, so an amount is a {@code Money} everywhere in the
 * domain and a plain decimal only in the database. That matters because the
 * rounding rule lives on {@code Money}: a field typed {@code BigDecimal} can be
 * multiplied or divided anywhere with any rounding mode, and the ledger's
 * balanced-transaction trigger would be the first thing to notice.
 */
@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Money money) {
        return money == null ? null : money.amount();
    }

    @Override
    public Money convertToEntityAttribute(BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }
}
