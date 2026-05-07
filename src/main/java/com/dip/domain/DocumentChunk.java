package com.dip.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "document_chunks")
@Data
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "artifact_id", nullable = false)
    private DocumentArtifact artifact;
    
    // Content is NOT stored in PostgreSQL - only in Qdrant vector store
    // This field is transient and only used during ingestion
    @Transient
    private String content;
    
    private String section;
    
    @Enumerated(EnumType.STRING)
    private ChunkType chunkType;
    
    private String vectorId;
    
    private Integer sectionNumber;
    
    // Metadata about the chunk (not the full content)
    private Integer contentLength;
    
    @Column(columnDefinition = "TEXT")
    private String metadata;
}
