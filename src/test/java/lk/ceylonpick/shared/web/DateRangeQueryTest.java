package lk.ceylonpick.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DateRangeQueryTest {

    /**
     * Colombo is UTC+05:30. Resolving the day in UTC instead would start the
     * report five and a half hours late and drop that morning's orders.
     */
    @Test
    void resolvesTheStartOfTheDayInColombo() {
        DateRangeQuery range = new DateRangeQuery(LocalDate.of(2026, 9, 6), null);
        assertThat(range.fromInstant()).isEqualTo(Instant.parse("2026-09-05T18:30:00Z"));
    }

    /** The end date is inclusive, so the bound is the start of the following day. */
    @Test
    void includesTheWholeOfTheEndDate() {
        DateRangeQuery range = new DateRangeQuery(null, LocalDate.of(2026, 9, 6));
        assertThat(range.toInstantExclusive()).isEqualTo(Instant.parse("2026-09-06T18:30:00Z"));
    }

    @Test
    void aSingleDayCoversExactlyTwentyFourHours() {
        LocalDate day = LocalDate.of(2026, 9, 6);
        DateRangeQuery range = new DateRangeQuery(day, day);
        assertThat(java.time.Duration.between(range.fromInstant(), range.toInstantExclusive()).toHours())
                .isEqualTo(24);
    }

    @Test
    void treatsBothEndsAsOptional() {
        DateRangeQuery empty = new DateRangeQuery(null, null);
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.fromInstant()).isNull();
        assertThat(empty.toInstantExclusive()).isNull();
    }

    /** A reversed range would quietly return nothing; say so instead. */
    @Test
    void rejectsAReversedRange() {
        DateRangeQuery reversed = new DateRangeQuery(LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 1));
        assertThatThrownBy(reversed::validated)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("before the start date");
    }

    @Test
    void acceptsAForwardRange() {
        DateRangeQuery range = new DateRangeQuery(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 6));
        assertThat(range.validated()).isSameAs(range);
    }
}
