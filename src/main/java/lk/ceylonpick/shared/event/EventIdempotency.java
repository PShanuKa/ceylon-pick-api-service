package lk.ceylonpick.shared.event;

import java.time.Clock;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-NOT-04: "All notifications shall be event-driven via the outbox and
 * idempotent per event id."
 *
 * <p>The outbox dispatches at least once, so a consumer will see the same event
 * twice — after a crash between doing the work and marking it dispatched, for
 * instance. {@link #claim} is how a consumer asks "is this mine to do?", and the
 * answer comes from a primary-key insert rather than a read-then-write, so two
 * dispatchers racing on the same event still produce exactly one winner.
 */
@Service
public class EventIdempotency {

    private final ProcessedEventRepository processed;
    private final Clock clock;

    public EventIdempotency(ProcessedEventRepository processed, Clock clock) {
        this.processed = processed;
        this.clock = clock;
    }

    /**
     * Marks this event as handled by this consumer.
     *
     * <p>Commits on its own so the claim survives a later failure in the
     * consumer's own transaction — a consumer that half-succeeded must not get a
     * second free attempt at the side effect it already performed.
     *
     * @return true if the caller should do the work; false if someone already has
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String consumer, String eventId) {
        try {
            processed.saveAndFlush(new ProcessedEvent(consumer, eventId, clock.instant()));
            return true;
        } catch (DataIntegrityViolationException alreadyClaimed) {
            return false;
        }
    }
}
