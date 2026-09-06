package lk.ceylonpick.shared.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * An inclusive date range for the admin queues and statements.
 *
 * <p>Dates arrive as plain {@code yyyy-MM-dd} because that is what the operator
 * typed, and are widened here to the instants that bound that day in
 * {@code Asia/Colombo}. Timestamps are stored UTC and displayed Colombo
 * (SRS §7), so resolving the day in the wrong zone silently drops the first or
 * last five and a half hours of a report.
 */
public record DateRangeQuery(LocalDate from, LocalDate to) {

    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Colombo");

    /** Start of the {@code from} day, or null for "no lower bound". */
    public Instant fromInstant() {
        return from == null ? null : from.atStartOfDay(DISPLAY_ZONE).toInstant();
    }

    /** Start of the day after {@code to}, so the whole of {@code to} is included. */
    public Instant toInstantExclusive() {
        return to == null ? null : to.plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant();
    }

    public boolean isEmpty() {
        return from == null && to == null;
    }

    /** Rejects a reversed range rather than silently returning nothing. */
    public DateRangeQuery validated() {
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "The end date is before the start date");
        }
        return this;
    }
}
