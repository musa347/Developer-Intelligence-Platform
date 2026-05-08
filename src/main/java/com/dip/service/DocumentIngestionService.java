package com.dip.service;

import com.dip.domain.*;
import com.dip.repository.DocumentArtifactRepository;
import com.dip.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {
    
    private final DocumentArtifactRepository artifactRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentChunkingService documentChunkingService;
    private final PIIMaskingService piiMaskingService;
    private final EmbeddingService embeddingService;
    private final ServiceRegistryService serviceRegistryService;
    private final VectorStoreService vectorStoreService;
    

    public CompletableFuture<DocumentArtifact> ingestDocumentAsync(
        String serviceCode,
        String content,
        DocumentType documentType,
        String version,
        String sourceReference) {
        
        log.info("[DEBUG] Starting async document ingestion for service: {}, type: {}", serviceCode, documentType);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ingestDocument(serviceCode, content, documentType, version, sourceReference);
            } catch (Exception e) {
                log.error("[DEBUG] Async document ingestion failed: {}", e.getMessage(), e);
                throw new RuntimeException("Async ingestion failed: " + e.getMessage(), e);
            }
        });
    }
    
    @Transactional
    public DocumentArtifact ingestDocument(
        String serviceCode,
        String content,
        DocumentType documentType,
        String version,
        String sourceReference
    ) throws ExecutionException, InterruptedException {
        log.info("[DEBUG] Starting document ingestion for service: {}, type: {}", serviceCode, documentType);

        com.dip.domain.Service service = serviceRegistryService.getServiceByCode(serviceCode);
        log.info("[DEBUG] Found service: {} (ID: {})", service.getName(), service.getId());

        // Clean null bytes to prevent PostgreSQL errors
        String cleanedContent = content.replace("\u0000", "");

        // Create artifact with content that will be persisted in PostgreSQL
        DocumentArtifact artifact = new DocumentArtifact();
        artifact.setService(service);
        artifact.setDocumentType(documentType);
        artifact.setVersion(version);
        artifact.setSourceReference(sourceReference);
        artifact.setEffectiveDate(LocalDate.now());
        artifact.setContent(cleanedContent);
        artifact.setContentLength(cleanedContent.length());
        
        // Save artifact with content to PostgreSQL
        artifact = artifactRepository.save(artifact);
        log.info("[DEBUG] Saved artifact with content to PostgreSQL with ID: {}", artifact.getId());
        log.info("[DEBUG] Artifact content length: {}, content preview: {}", 
                artifact.getContent() != null ? artifact.getContent().length() : 0,
                artifact.getContent() != null && artifact.getContent().length() > 50 ? 
                        artifact.getContent().substring(0, 50) + "..." : artifact.getContent());

        // Use improved chunking service
        List<DocumentChunk> chunks = documentChunkingService.chunkDocument(artifact.getId().toString(), documentType, cleanedContent);
        
        // Set artifact reference and chunk count
        for (DocumentChunk chunk : chunks) {
            chunk.setArtifact(artifact);
        }
        
        artifact.setChunkCount(chunks.size());
        chunks = chunkRepository.saveAll(chunks);
        log.info("[DEBUG] Saved {} chunks to database", chunks.size());
        
        // Debug: Check if content is actually saved
        for (int j = 0; j < Math.min(chunks.size(), 3); j++) {
            DocumentChunk chunk = chunks.get(j);
            log.info("[DEBUG] Chunk {} content length: {}, content preview: {}", 
                    chunk.getId(), 
                    chunk.getContent() != null ? chunk.getContent().length() : 0,
                    chunk.getContent() != null && chunk.getContent().length() > 50 ? 
                            chunk.getContent().substring(0, 50) + "..." : chunk.getContent());
        }

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            
            log.info("[DEBUG] Processing chunk {}/{} (vectorId: {})", i + 1, chunks.size(), chunk.getVectorId());
            try {
                // Apply PII masking for security, also strip any residual null bytes
                String maskedContent = piiMaskingService.maskPII(chunk.getContent()).replace("\u0000", "");
                
                // Generate embedding for masked content
                float[] embedding = embeddingService.generateEmbedding(maskedContent);
                log.info("[DEBUG] Generated embedding with {} dimensions", embedding.length);

                // Create comprehensive metadata - content is stored in BOTH PostgreSQL and Qdrant
                Map<String, Object> payload = new HashMap<>();
                payload.put("service_id", service.getId());
                payload.put("service_code", service.getServiceCode());
                payload.put("service_name", service.getName());
                payload.put("artifact_id", artifact.getId());
                payload.put("chunk_id", String.valueOf(chunk.getId()));
                payload.put("section", chunk.getSection());
                payload.put("section_number", chunk.getSectionNumber());
                payload.put("chunk_type", chunk.getChunkType().name());
                payload.put("document_type", documentType.name());
                payload.put("version", version);
                payload.put("source_reference", sourceReference);
                payload.put("effective_date", artifact.getEffectiveDate().toString());
                payload.put("content", maskedContent); // FULL CONTENT also stored in Qdrant for search
                payload.put("content_length", maskedContent.length());
                payload.put("has_pii", !maskedContent.equals(chunk.getContent())); // Flag if PII was found
                
                log.info("[DEBUG] Upserting vector to Qdrant with ID: {}", chunk.getVectorId());

                vectorStoreService.upsertVector(chunk.getVectorId(), embedding, payload);
                log.info("[DEBUG] Successfully upserted vector {} to Qdrant (content also stored in PostgreSQL)", chunk.getVectorId());
            } catch (Exception e) {
                log.error("[DEBUG] FAILED to process chunk {}: {}", chunk.getVectorId(), e.getMessage(), e);
                continue;
            }
        }
        
        log.info("[DEBUG] Document ingestion completed successfully for artifact: {}", artifact.getId());
        return artifact;
    }
}
