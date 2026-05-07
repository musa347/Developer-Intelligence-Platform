package com.dip.service;

import com.dip.domain.ServiceStatus;
import com.dip.domain.User;
import com.dip.dto.ServiceWithOwnerRequest;
import com.dip.repository.ServiceRepository;
import com.dip.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceRegistryService {
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    
    public com.dip.domain.Service registerService(com.dip.domain.Service service) {
        return serviceRepository.save(service);
    }
    
    public com.dip.domain.Service registerServiceWithOwner(ServiceWithOwnerRequest request) {
        com.dip.domain.Service service = new com.dip.domain.Service();
        service.setServiceCode(request.getServiceCode());
        service.setName(request.getName());
        service.setDomain(request.getDomain());
        service.setOwningTeam(request.getOwningTeam());
        service.setStatus(request.getStatus());
        
        if (request.getOwnerId() != null) {
            User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getOwnerId()));
            service.setOwner(owner);
        }
        
        return serviceRepository.save(service);
    }
    
    public com.dip.domain.Service getServiceByCode(String serviceCode) {
        return serviceRepository.findByServiceCode(serviceCode)
            .orElseThrow(() -> new RuntimeException("Service not found: " + serviceCode));
    }
    
    public List<com.dip.domain.Service> getAllActiveServices() {
        return serviceRepository.findByStatus(ServiceStatus.ACTIVE);
    }
    
    public List<com.dip.domain.Service> getAllServices() {
        return serviceRepository.findAll();
    }
    
    public com.dip.domain.Service updateService(com.dip.domain.Service service) {
        var existing = getServiceByCode(service.getServiceCode());
        existing.setName(service.getName());
        existing.setDomain(service.getDomain());
        existing.setOwningTeam(service.getOwningTeam());
        existing.setStatus(service.getStatus());
        return serviceRepository.save(existing);
    }
    
    public void deleteService(String serviceCode) {
        var service = getServiceByCode(serviceCode);
        serviceRepository.delete(service);
    }
}
