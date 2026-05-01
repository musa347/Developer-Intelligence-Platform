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
    private final DocumentParser documentParser;
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

        DocumentArtifact artifact = new DocumentArtifact();
        artifact.setService(service);
        artifact.setDocumentType(documentType);
        artifact.setVersion(version);
        artifact.setSourceReference(sourceReference);
        artifact.setEffectiveDate(LocalDate.now());
        artifact.setContent(content);
        artifact = artifactRepository.save(artifact);
        log.info("[DEBUG] Saved artifact with ID: {}", artifact.getId());

        List<DocumentParser.ParsedChunk> parsedChunks = switch (documentType) {
            case MARKDOWN, README -> documentParser.parseMarkdown(content);
            case OPENAPI, SWAGGER -> documentParser.parseOpenAPI(content);
            default -> documentParser.parseText(content);
        };
        
        List<DocumentChunk> chunks = new ArrayList<>();
        for (DocumentParser.ParsedChunk parsed : parsedChunks) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setArtifact(artifact);
            chunk.setContent(parsed.getContent());
            chunk.setSection(parsed.getSection());
            chunk.setChunkType(parsed.getChunkType());
            chunk.setVectorId(UUID.randomUUID().toString());
            chunks.add(chunk);
        }
        
        chunks = chunkRepository.saveAll(chunks);
        log.info("[DEBUG] Saved {} chunks to database", chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            DocumentParser.ParsedChunk parsed = parsedChunks.get(i);
            
            log.info("[DEBUG] Generating embedding for chunk {}/{} (vectorId: {})", i + 1, chunks.size(), chunk.getVectorId());
            try {
                float[] embedding = embeddingService.generateEmbedding(parsed.getContent());
                log.info("[DEBUG] Generated embedding with {} dimensions", embedding.length);

                Map<String, Object> payload = new HashMap<>();
                payload.put("service_id", service.getId());
                payload.put("chunk_id", String.valueOf(chunk.getId()));
                payload.put("section", parsed.getSection());
                payload.put("chunk_type", parsed.getChunkType().name());
                
                log.info("[DEBUG] Upserting vector to Qdrant with ID: {}, payload keys: {}", chunk.getVectorId(), payload.keySet());

                vectorStoreService.upsertVector(chunk.getVectorId(), embedding, payload);
                log.info("[DEBUG] Successfully upserted vector {}", chunk.getVectorId());
            } catch (Exception e) {
                log.error("[DEBUG] FAILED to process chunk {}: {}", chunk.getVectorId(), e.getMessage(), e);
                continue;
            }
        }
        
        log.info("[DEBUG] Document ingestion completed successfully for artifact: {}", artifact.getId());
        return artifact;
    }
}
