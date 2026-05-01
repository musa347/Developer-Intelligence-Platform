package com.dip.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_artifacts")
@Data
public class DocumentArtifact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;
    
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(20)")
    private DocumentType documentType;
    
    private String version;
    private String sourceReference;
    private LocalDate effectiveDate;
    private LocalDateTime ingestionTimestamp;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @PrePersist
    protected void onCreate() {
        ingestionTimestamp = LocalDateTime.now();
    }
}
