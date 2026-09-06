package lk.ceylonpick.shared.event;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row per (consumer, event) pair — the record that stops a replay repeating work. */
@Entity
@Table(name = "processed_event")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEvent {

    @Embeddable
    public record Key(
            @Column(name = "consumer", nullable = false) String consumer,
            @Column(name = "event_id", nullable = false) String eventId) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent(String consumer, String eventId, Instant processedAt) {
        this.id = new Key(consumer, eventId);
        this.processedAt = processedAt;
    }
}
