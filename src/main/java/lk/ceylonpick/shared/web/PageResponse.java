package lk.ceylonpick.shared.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * One page of results, as the {@code data} of an {@link ApiResponse}.
 *
 * <p>A deliberately small subset of Spring Data's {@code Page}, which serialises
 * its internal {@code Pageable} and sort structures into the response and ties
 * the wire format to the persistence library.
 */
public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** Maps entities to DTOs without materialising an intermediate page. */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
