package lk.ceylonpick.shared.web;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Paging and sorting parameters, shared by every list endpoint.
 *
 * <p>Two things it enforces that a raw {@code Pageable} does not. The page size
 * is capped, so no caller can ask for the whole table in one request. And the
 * sort field is checked against a whitelist the endpoint supplies, because it
 * reaches the {@code ORDER BY} clause — an unchecked field name is both an
 * injection surface and an easy way to sort by an unindexed column.
 */
public record PageQuery(Integer page, Integer size, String sort, String direction) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** Unsorted, for endpoints whose repository query already has an ORDER BY. */
    public Pageable toPageable() {
        return PageRequest.of(pageOrDefault(), sizeOrDefault());
    }

    /**
     * @param sortable      field names this endpoint allows sorting by
     * @param defaultSort   used when the caller asks for nothing, or for something not allowed
     */
    public Pageable toPageable(Set<String> sortable, String defaultSort) {
        String field = sort != null && sortable.contains(sort) ? sort : defaultSort;
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(pageOrDefault(), sizeOrDefault(), Sort.by(dir, field));
    }
}
