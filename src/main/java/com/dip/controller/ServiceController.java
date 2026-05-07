package com.dip.controller;

import com.dip.domain.Service;
import com.dip.domain.UserRole;
import com.dip.dto.ServiceWithOwnerRequest;
import com.dip.security.RoleRequired;
import com.dip.service.ServiceRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {
    private final ServiceRegistryService serviceRegistryService;
    
    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<Service> registerService(@RequestBody Service service) {
        return ResponseEntity.ok(serviceRegistryService.registerService(service));
    }
    
    @PostMapping("/with-owner")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<Service> registerServiceWithOwner(@RequestBody ServiceWithOwnerRequest request) {
        return ResponseEntity.ok(serviceRegistryService.registerServiceWithOwner(request));
    }
    
    @GetMapping
    public ResponseEntity<List<Service>> getAllServices() {
        return ResponseEntity.ok(serviceRegistryService.getAllServices());
    }
    
    @GetMapping("/active")
    public ResponseEntity<List<Service>> getActiveServices() {
        return ResponseEntity.ok(serviceRegistryService.getAllActiveServices());
    }
    
    @GetMapping("/{serviceCode}")
    public ResponseEntity<Service> getService(@PathVariable String serviceCode) {
        return ResponseEntity.ok(serviceRegistryService.getServiceByCode(serviceCode));
    }
    
    @PutMapping("/{serviceCode}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<Service> updateService(@PathVariable String serviceCode, @RequestBody Service service) {
        service.setServiceCode(serviceCode);
        return ResponseEntity.ok(serviceRegistryService.updateService(service));
    }
    
    @DeleteMapping("/{serviceCode}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<Void> deleteService(@PathVariable String serviceCode) {
        serviceRegistryService.deleteService(serviceCode);
        return ResponseEntity.noContent().build();
    }
}
