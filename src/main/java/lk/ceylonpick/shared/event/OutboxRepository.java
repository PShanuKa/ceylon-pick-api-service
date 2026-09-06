package lk.ceylonpick.shared.event;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEntry, String> {

    /** Oldest first, so events reach consumers in the order they happened. */
    @Query("select o from OutboxEntry o where o.dispatchedAt is null order by o.occurredAt asc")
    List<OutboxEntry> findPending(Pageable pageable);

    long countByDispatchedAtIsNull();
}
