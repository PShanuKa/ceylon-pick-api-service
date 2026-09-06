package lk.ceylonpick.shared.event;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.shared.Ids;
import tools.jackson.databind.ObjectMapper;

/**
 * Records an event in the same transaction as the change that caused it.
 *
 * <p>{@code MANDATORY} propagation is the whole point: publishing outside a
 * transaction would let an event commit while the state change rolls back, or
 * the reverse. Requiring an existing transaction turns that mistake into a
 * startup-visible failure instead of a rare inconsistency.
 */
@Service
public class OutboxPublisher {

    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final Clock clock;

    public OutboxPublisher(OutboxRepository outbox, ObjectMapper mapper, Clock clock) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** @return the event id, which consumers deduplicate on (FR-NOT-04) */
    @Transactional(propagation = Propagation.MANDATORY)
    public String publish(DomainEvent event) {
        OutboxEntry entry = new OutboxEntry();
        entry.setId(Ids.newId());
        entry.setAggregateType(event.aggregateType());
        entry.setAggregateId(event.aggregateId());
        entry.setEventType(event.eventType());
        entry.setPayload(mapper.writeValueAsString(event));
        entry.setOccurredAt(clock.instant());
        outbox.save(entry);
        return entry.getId();
    }
}
