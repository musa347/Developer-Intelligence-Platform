package com.dip.service;

import com.dip.domain.*;
import com.dip.repository.DocumentArtifactRepository;
import com.dip.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {
    
    private final DocumentArtifactRepository artifactRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentParser documentParser;
    private final EmbeddingService embeddingService;
    private final ServiceRegistryService serviceRegistryService;
    private final VectorStoreService vectorStoreService;
    
    @Transactional
    public DocumentArtifact ingestDocument(
        String serviceCode,
        String content,
        DocumentType documentType,
        String version,
        String sourceReference
    ) throws ExecutionException, InterruptedException {
        com.dip.domain.Service service = serviceRegistryService.getServiceByCode(serviceCode);
        
        DocumentArtifact artifact = new DocumentArtifact();
        artifact.setService(service);
        artifact.setDocumentType(documentType);
        artifact.setVersion(version);
        artifact.setSourceReference(sourceReference);
        artifact.setEffectiveDate(LocalDate.now());
        artifact.setContent(content);
        artifact = artifactRepository.save(artifact);
        
        List<DocumentParser.ParsedChunk> parsedChunks = switch (documentType) {
            case MARKDOWN, README -> documentParser.parseMarkdown(content);
            case OPENAPI, SWAGGER -> documentParser.parseOpenAPI(content);
            default -> List.of();
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
        
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            DocumentParser.ParsedChunk parsed = parsedChunks.get(i);
            
            float[] embedding = embeddingService.generateEmbedding(parsed.getContent());
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("service_id", service.getId());
            payload.put("chunk_id", String.valueOf(chunk.getId()));
            payload.put("section", parsed.getSection());
            payload.put("chunk_type", parsed.getChunkType().name());
            
            vectorStoreService.upsertVector(chunk.getVectorId(), embedding, payload);
        }
        
        return artifact;
    }
}
