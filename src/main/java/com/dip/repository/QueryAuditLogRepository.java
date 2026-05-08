package com.dip.repository;

import com.dip.domain.QueryAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QueryAuditLogRepository extends JpaRepository<QueryAuditLog, String> {
    List<QueryAuditLog> findByUserId(String userId);
    List<QueryAuditLog> findByServiceId(Long serviceId);
}
