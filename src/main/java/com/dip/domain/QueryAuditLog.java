package com.dip.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "query_audit_logs")
@Data
public class QueryAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String queryId;
    
    private LocalDateTime timestamp;
    private String userId;
    
    @ManyToOne
    @JoinColumn(name = "service_id")
    private Service service;
    
    @Column(columnDefinition = "TEXT")
    private String query;
    
    @Enumerated(EnumType.STRING)
    private QueryIntent intent;
    
    @Column(columnDefinition = "TEXT")
    private String retrievedChunks;
    
    @Column(columnDefinition = "TEXT")
    private String response;
    
    @Column(columnDefinition = "TEXT")
    private String sources;
    
    private Long latencyMs;
    
    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
