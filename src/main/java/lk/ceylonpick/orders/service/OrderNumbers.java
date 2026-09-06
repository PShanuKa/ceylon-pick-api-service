package lk.ceylonpick.orders.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues the human-facing order number, e.g. {@code CP-10231}.
 *
 * <p>Drawn from a database sequence rather than a count or a max: sequences do
 * not hand the same value to two concurrent checkouts, and they do not go
 * backwards when an order is deleted. The number is also the only identifier a
 * buyer ever quotes, so it must be short and stable — which is why it is
 * separate from the ULID primary key.
 *
 * <p>{@code REQUIRES_NEW} because sequence values are deliberately not
 * transactional: taking the number in its own transaction means a rolled-back
 * checkout burns a number rather than blocking the next one.
 */
@Component
public class OrderNumbers {

    private static final String PREFIX = "CP-";

    private final JdbcTemplate jdbc;

    public OrderNumbers(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        Long value = jdbc.queryForObject("select nextval('order_number_seq')", Long.class);
        return PREFIX + value;
    }
}
