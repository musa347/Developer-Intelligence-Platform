package com.dip.service;

import com.dip.domain.*;
import com.dip.repository.DocumentArtifactRepository;
import com.dip.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

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
    private final Map<String, IngestionJobStatus> ingestionJobs = new ConcurrentHashMap<>();
    private static final Pattern NULL_CHAR_PATTERN = Pattern.compile("\\u0000");
    

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

    public String startIngestionJob(
            String serviceCode,
            String content,
            DocumentType documentType,
            String version,
            String sourceReference) {
        String jobId = UUID.randomUUID().toString();
        IngestionJobStatus jobStatus = new IngestionJobStatus(
                jobId,
                serviceCode,
                documentType != null ? documentType.name() : null,
                version,
                sourceReference
        );
        ingestionJobs.put(jobId, jobStatus);

        CompletableFuture.supplyAsync(() -> {
            try {
                DocumentArtifact artifact = ingestDocument(serviceCode, content, documentType, version, sourceReference);
                jobStatus.markCompleted(artifact.getId(), artifact.getChunkCount());
                return artifact;
            } catch (Exception e) {
                log.error("[DEBUG] Ingestion job {} failed: {}", jobId, e.getMessage(), e);
                jobStatus.markFailed(e.getMessage());
                throw new RuntimeException("Ingestion job failed: " + e.getMessage(), e);
            }
        });

        return jobId;
    }

    public Optional<Map<String, Object>> getIngestionJobStatus(String jobId) {
        IngestionJobStatus status = ingestionJobs.get(jobId);
        if (status == null) {
            return Optional.empty();
        }
        return Optional.of(status.toMap());
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

        // Delete existing artifacts and chunks for this service+type+version to prevent duplicates
        List<DocumentArtifact> existing = artifactRepository.findByServiceIdAndDocumentType(service.getId(), documentType);
        existing.stream()
                .filter(a -> version.equals(a.getVersion()))
                .forEach(a -> {
                    chunkRepository.deleteAll(chunkRepository.findByArtifactId(a.getId()));
                    artifactRepository.delete(a);
                });
        log.info("[DEBUG] Removed {} existing artifact(s) for service={}, type={}, version={}",
                existing.size(), serviceCode, documentType, version);

        // Clean null bytes aggressively to prevent PostgreSQL 0x00 errors
        String cleanedContent = sanitizeText(content);
        String cleanedVersion = sanitizeText(version);
        String cleanedSourceReference = sanitizeText(sourceReference);

        // Create artifact with content that will be persisted in PostgreSQL
        DocumentArtifact artifact = new DocumentArtifact();
        artifact.setService(service);
        artifact.setDocumentType(documentType);
        artifact.setVersion(cleanedVersion);
        artifact.setSourceReference(cleanedSourceReference);
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
                // Apply PII masking and sanitize again before storing in downstream systems
                String rawChunkContent = sanitizeText(chunk.getContent());
                chunk.setContent(rawChunkContent);
                chunk.setSection(sanitizeText(chunk.getSection()));
                String maskedContent = sanitizeText(piiMaskingService.maskPII(rawChunkContent));

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
                payload.put("section", sanitizeText(chunk.getSection()));
                payload.put("section_number", chunk.getSectionNumber());
                payload.put("chunk_type", chunk.getChunkType().name());
                payload.put("document_type", documentType.name());
                payload.put("version", cleanedVersion);
                payload.put("source_reference", cleanedSourceReference);
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

    private String sanitizeText(String value) {
        if (value == null) {
            return "";
        }
        return NULL_CHAR_PATTERN.matcher(value).replaceAll("");
    }

    private static class IngestionJobStatus {
        private final String jobId;
        private final String serviceCode;
        private final String documentType;
        private final String version;
        private final String sourceReference;
        private final Instant createdAt;
        private volatile Instant updatedAt;
        private volatile String status;
        private volatile Long artifactId;
        private volatile Integer chunkCount;
        private volatile String error;

        private IngestionJobStatus(
                String jobId,
                String serviceCode,
                String documentType,
                String version,
                String sourceReference) {
            this.jobId = jobId;
            this.serviceCode = serviceCode;
            this.documentType = documentType;
            this.version = version;
            this.sourceReference = sourceReference;
            this.createdAt = Instant.now();
            this.updatedAt = this.createdAt;
            this.status = "PROCESSING";
        }

        private void markCompleted(Long artifactId, Integer chunkCount) {
            this.status = "COMPLETED";
            this.artifactId = artifactId;
            this.chunkCount = chunkCount;
            this.error = null;
            this.updatedAt = Instant.now();
        }

        private void markFailed(String errorMessage) {
            this.status = "FAILED";
            this.error = errorMessage;
            this.updatedAt = Instant.now();
        }

        private Map<String, Object> toMap() {
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", status);
            response.put("serviceCode", serviceCode);
            response.put("documentType", documentType);
            response.put("version", version);
            response.put("sourceReference", sourceReference);
            response.put("artifactId", artifactId);
            response.put("chunkCount", chunkCount);
            response.put("error", error);
            response.put("createdAt", createdAt.toString());
            response.put("updatedAt", updatedAt.toString());
            return response;
        }
    }
}
