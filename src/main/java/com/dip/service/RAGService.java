package com.dip.service;

import com.dip.domain.DocumentChunk;
import com.dip.domain.QueryAuditLog;
import com.dip.dto.QueryResponse;
import com.dip.repository.DocumentChunkRepository;
import com.dip.repository.QueryAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RAGService {
    
    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository chunkRepository;
    private final QueryAuditLogRepository auditRepository;
    private final ServiceRegistryService serviceRegistryService;
    private final LLMService llmService;
    private final VectorStoreService vectorStoreService;
    
    public QueryResponse query(String serviceCode, String query, String userId) 
            throws ExecutionException, InterruptedException {
        long startTime = System.currentTimeMillis();
        
        com.dip.domain.Service service = serviceRegistryService.getServiceByCode(serviceCode);
        
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        
        List<String> chunkIds = vectorStoreService.searchSimilar(
            queryEmbedding,
            service.getId(),
            5,
            null
        );
        
        List<DocumentChunk> relevantChunks = chunkRepository.findAllById(
            chunkIds.stream().map(Long::parseLong).toList()
        );
        
        String context = relevantChunks.stream()
            .map(DocumentChunk::getContent)
            .collect(Collectors.joining("\n\n---\n\n"));
        
        String answer = llmService.generateAnswer(service.getName(), query, context);
        
        QueryResponse response = new QueryResponse();
        response.setAnswer(answer);
        response.setServiceCode(serviceCode);
        response.setConfidence(relevantChunks.isEmpty() ? "low" : "high");
        response.setSources(relevantChunks.stream()
            .map(chunk -> {
                QueryResponse.SourceReference ref = new QueryResponse.SourceReference();
                ref.setDocumentType(chunk.getArtifact().getDocumentType().name());
                ref.setVersion(chunk.getArtifact().getVersion());
                ref.setSection(chunk.getSection());
                ref.setExcerpt(chunk.getContent().substring(0, Math.min(200, chunk.getContent().length())));
                return ref;
            })
            .collect(Collectors.toList())
        );
        
        auditQuery(service, query, userId, relevantChunks, answer, System.currentTimeMillis() - startTime);
        
        return response;
    }
    
    private void auditQuery(com.dip.domain.Service service, String query, String userId, 
                           List<DocumentChunk> chunks, String answer, long latency) {
        QueryAuditLog log = new QueryAuditLog();
        log.setService(service);
        log.setQuery(query);
        log.setUserId(userId);
        log.setRetrievedChunks(chunks.size() + " chunks");
        log.setResponse(answer);
        log.setLatencyMs(latency);
        auditRepository.save(log);
    }
}
