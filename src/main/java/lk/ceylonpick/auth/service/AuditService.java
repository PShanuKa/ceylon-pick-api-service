package lk.ceylonpick.auth.service;

import java.time.Clock;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.domain.AuditLogEntry;
import lk.ceylonpick.auth.repo.AuditLogRepository;
import lk.ceylonpick.shared.Hashes;

/**
 * NFR-07: admin actions and settings changes logged with actor and time.
 *
 * <p>Every write runs in its own transaction. A failed sign-in ends by throwing,
 * which rolls the caller back — an audit row written in that transaction would
 * disappear with it, and the failures are exactly the ones worth keeping.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String actorId, String actorRole,
                       String entityType, String entityId, String ip) {
        record(action, actorId, actorRole, entityType, entityId, ip, null, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String actorId, String actorRole,
                       String entityType, String entityId, String ip,
                       Map<String, Object> before, Map<String, Object> after, String reason) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setAction(action);
        entry.setActorId(actorId);
        entry.setActorRole(actorRole);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setIpHash(Hashes.ipHash(ip));
        entry.setBefore(before);
        entry.setAfter(after);
        entry.setReason(reason);
        entry.setCreatedAt(clock.instant());
        repository.save(entry);
    }
}
