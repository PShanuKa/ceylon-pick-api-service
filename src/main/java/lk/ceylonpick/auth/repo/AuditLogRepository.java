package lk.ceylonpick.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.auth.domain.AuditLogEntry;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {
}
