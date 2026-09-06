package lk.ceylonpick.shared.event;

/**
 * Something that happened, recorded for other modules to react to.
 *
 * <p>Architecture §4: "Events are persisted in the outbox in the same
 * transaction as the state change, then dispatched by a poller
 * (at-least-once)." Publishing is therefore never a remote call inside a
 * business transaction — an order confirms or it does not, and the notification
 * cannot be the reason it fails.
 *
 * <p>At-least-once means duplicates are normal, not exceptional, which is why
 * FR-NOT-04 requires "every consumer is idempotent on event_id". See
 * {@link EventIdempotency}.
 */
public interface DomainEvent {

    /** The entity this happened to, e.g. {@code order}. */
    String aggregateType();

    String aggregateId();

    /** Past tense, e.g. {@code OrderConfirmed} (Architecture §4). */
    String eventType();
}
