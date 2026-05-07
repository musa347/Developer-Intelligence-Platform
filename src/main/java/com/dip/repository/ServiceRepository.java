package com.dip.repository;

import com.dip.domain.Service;
import com.dip.domain.ServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Long> {
    Optional<Service> findByServiceCode(String serviceCode);
    List<Service> findByStatus(ServiceStatus status);
    List<Service> findByDomain(String domain);
}
