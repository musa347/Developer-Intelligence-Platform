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
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    private String section;
    
    @Enumerated(EnumType.STRING)
    private ChunkType chunkType;
    
    private String vectorId;
    
    private Integer sectionNumber;
    
    @Column(columnDefinition = "TEXT")
    private String metadata;
}
