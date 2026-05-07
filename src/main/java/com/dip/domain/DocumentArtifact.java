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
    
    // Content is NOT stored in PostgreSQL - only in Qdrant vector store
    // This field is transient and only used during ingestion
    @Transient
    private String content;
    
    // Metadata about the content
    private Integer contentLength;
    private Integer chunkCount;
    
    @PrePersist
    protected void onCreate() {
        ingestionTimestamp = LocalDateTime.now();
    }
}
