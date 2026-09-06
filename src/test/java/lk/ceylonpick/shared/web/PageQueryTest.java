package lk.ceylonpick.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageQueryTest {

    private static final Set<String> SORTABLE = Set.of("createdAt", "total");

    @Test
    void fallsBackToSensibleDefaults() {
        PageQuery query = new PageQuery(null, null, null, null);
        assertThat(query.pageOrDefault()).isZero();
        assertThat(query.sizeOrDefault()).isEqualTo(20);
    }

    /** Without the cap, one request could pull the whole orders table. */
    @Test
    void capsThePageSize() {
        assertThat(new PageQuery(0, 5000, null, null).sizeOrDefault()).isEqualTo(100);
    }

    @Test
    void treatsNonsenseAsDefaults() {
        assertThat(new PageQuery(-3, 0, null, null).pageOrDefault()).isZero();
        assertThat(new PageQuery(-3, 0, null, null).sizeOrDefault()).isEqualTo(20);
    }

    /**
     * The sort field reaches the ORDER BY clause, so anything not on the
     * endpoint's whitelist is replaced rather than passed through.
     */
    @Test
    void ignoresASortFieldTheEndpointDidNotAllow() {
        Pageable pageable = new PageQuery(0, 10, "passwordHash", "asc").toPageable(SORTABLE, "createdAt");
        assertThat(pageable.getSort().getOrderFor("passwordHash")).isNull();
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void honoursAnAllowedSortField() {
        Pageable pageable = new PageQuery(2, 10, "total", "asc").toPageable(SORTABLE, "createdAt");
        Sort.Order order = pageable.getSort().getOrderFor("total");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getPageNumber()).isEqualTo(2);
    }

    /** Newest first is the useful default for every admin queue. */
    @Test
    void defaultsToDescending() {
        Pageable pageable = new PageQuery(0, 10, "total", null).toPageable(SORTABLE, "createdAt");
        assertThat(pageable.getSort().getOrderFor("total").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
