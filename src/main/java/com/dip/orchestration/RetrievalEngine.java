package com.dip.orchestration;

import com.dip.domain.ChunkType;
import com.dip.domain.DocumentChunk;
import com.dip.repository.DocumentChunkRepository;
import com.dip.service.EmbeddingService;
import com.dip.service.PIIMaskingService;
import com.dip.service.VectorStoreService;
import io.qdrant.client.grpc.Points.ScoredPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalEngine {
    
    private final EmbeddingService embeddingService;
    private final PIIMaskingService piiMaskingService;
    private final VectorStoreService vectorStoreService;
    private final DocumentChunkRepository chunkRepository;
    
    public List<RetrievalResult> retrieve(
            String query, 
            Long serviceId, 
            RetrievalStrategy strategy,
            int topK) throws ExecutionException, InterruptedException {
        
        log.debug("Starting retrieval for query: {}, strategy: {}, topK: {}", query, strategy, topK);
        
        // Apply PII masking to query for security
        String maskedQuery = piiMaskingService.maskPII(query);
        
        // Generate query embedding
        float[] queryEmbedding = embeddingService.generateEmbedding(maskedQuery);
        log.debug("Generated query embedding with {} dimensions", queryEmbedding.length);
        
        // Get chunk type filter based on strategy
        ChunkType filterType = getChunkTypeFilter(strategy);
        log.debug("Using chunk type filter: {}", filterType);
        
        // Search for similar vectors
        List<ScoredPoint> scoredPoints = vectorStoreService.searchSimilarWithScores(
                queryEmbedding, 
                serviceId, 
                topK * 2, // Get more candidates for better filtering
                filterType
        );
        
        log.debug("Found {} raw scored points", scoredPoints.size());
        
        // Convert to retrieval results and apply additional filtering
        List<RetrievalResult> results = convertToRetrievalResults(scoredPoints);
        
        // Apply strategy-specific filtering and ranking
        results = applyStrategySpecificFiltering(results, strategy, query);
        
        // Sort by score and limit to requested topK
        results = results.stream()
                .sorted((a, b) -> Float.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
        
        log.debug("Returning {} filtered results", results.size());
        return results;
    }
    
    public List<DocumentChunk> retrieveAsChunks(
            String query, 
            Long serviceId, 
            RetrievalStrategy strategy,
            int topK) throws ExecutionException, InterruptedException {
        
        List<RetrievalResult> results = retrieve(query, serviceId, strategy, topK);
        
        // Convert RetrievalResults to DocumentChunks
        List<Long> chunkIds = results.stream()
                .map(RetrievalResult::getChunkId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        if (chunkIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Retrieve chunks and preserve order/score information
        List<DocumentChunk> chunks = chunkRepository.findAllById(chunkIds);
        
        // Sort chunks according to retrieval results order
        Map<Long, DocumentChunk> chunkMap = chunks.stream()
                .collect(Collectors.toMap(DocumentChunk::getId, chunk -> chunk));
        
        List<DocumentChunk> orderedChunks = new ArrayList<>();
        for (RetrievalResult result : results) {
            DocumentChunk chunk = chunkMap.get(result.getChunkId());
            if (chunk != null) {
                orderedChunks.add(chunk);
            }
        }
        
        return orderedChunks;
    }
    
    private ChunkType getChunkTypeFilter(RetrievalStrategy strategy) {
        return switch (strategy) {
            case DOCUMENTATION -> ChunkType.SECTION;
            case ENDPOINT_DRILLDOWN -> ChunkType.API_SPEC;
            case ARCHITECTURE -> ChunkType.SECTION;
            case TROUBLESHOOTING -> ChunkType.PARAGRAPH;
            case VERSION_DIFF -> ChunkType.SECTION;
            default -> null; // No filtering
        };
    }
    
    private List<RetrievalResult> convertToRetrievalResults(List<ScoredPoint> scoredPoints) {
        List<RetrievalResult> results = new ArrayList<>();
        
        for (ScoredPoint point : scoredPoints) {
            try {
                String vectorId = point.getId().getUuid();
                float score = point.getScore();
                
                // Extract payload information
                Map<String, Object> payload = new HashMap<>();
                if (point.getPayloadMap() != null && !point.getPayloadMap().isEmpty()) {
                    point.getPayloadMap().forEach((key, value) -> {
                        if (value.hasStringValue()) {
                            payload.put(key, value.getStringValue());
                        } else if (value.hasIntegerValue()) {
                            payload.put(key, value.getIntegerValue());
                        } else if (value.hasBoolValue()) {
                            payload.put(key, value.getBoolValue());
                        } else {
                            payload.put(key, value.toString());
                        }
                    });
                }
                
                // Extract chunk ID from payload
                Long chunkId = null;
                if (payload.containsKey("chunk_id")) {
                    Object chunkIdObj = payload.get("chunk_id");
                    if (chunkIdObj instanceof String) {
                        chunkId = Long.parseLong((String) chunkIdObj);
                    } else if (chunkIdObj instanceof Integer) {
                        chunkId = ((Integer) chunkIdObj).longValue();
                    } else if (chunkIdObj instanceof Long) {
                        chunkId = (Long) chunkIdObj;
                    }
                }
                
                String content = (String) payload.get("content");
                String section = (String) payload.get("section");
                String chunkType = (String) payload.get("chunk_type");
                
                RetrievalResult result = new RetrievalResult(
                        vectorId,
                        chunkId,
                        content,
                        section,
                        chunkType,
                        score,
                        payload
                );
                
                results.add(result);
                
            } catch (Exception e) {
                log.warn("Failed to convert scored point to retrieval result: {}", e.getMessage());
            }
        }
        
        return results;
    }
    
    private List<RetrievalResult> applyStrategySpecificFiltering(
            List<RetrievalResult> results, 
            RetrievalStrategy strategy, 
            String query) {
        
        // Apply minimum score threshold
        results = results.stream()
                .filter(result -> result.getScore() > 0.3f) // Minimum relevance threshold
                .collect(Collectors.toList());
        
        // Strategy-specific filtering
        return switch (strategy) {
            case ENDPOINT_DRILLDOWN -> filterForEndpointDrilldown(results, query);
            case TROUBLESHOOTING -> filterForTroubleshooting(results, query);
            case ARCHITECTURE -> filterForArchitecture(results, query);
            case VERSION_DIFF -> filterForVersionDiff(results, query);
            default -> results; // No additional filtering for DOCUMENTATION
        };
    }
    
    private List<RetrievalResult> filterForEndpointDrilldown(List<RetrievalResult> results, String query) {
        // Prioritize API specifications and endpoint-related content
        return results.stream()
                .filter(result -> {
                    String content = result.getContent().toLowerCase();
                    String section = result.getSection().toLowerCase();
                    String queryLower = query.toLowerCase();
                    
                    // Boost for API-related content
                    if (content.contains("endpoint") || content.contains("api") || 
                        content.contains("request") || content.contains("response") ||
                        section.contains("endpoint") || section.contains("api")) {
                        return true;
                    }
                    
                    // Check if query contains API-related terms
                    return queryLower.contains("endpoint") || queryLower.contains("api") ||
                           queryLower.contains("request") || queryLower.contains("response");
                })
                .collect(Collectors.toList());
    }
    
    private List<RetrievalResult> filterForTroubleshooting(List<RetrievalResult> results, String query) {
        // Prioritize troubleshooting and error-related content
        return results.stream()
                .filter(result -> {
                    String content = result.getContent().toLowerCase();
                    String section = result.getSection().toLowerCase();
                    
                    return content.contains("error") || content.contains("issue") ||
                           content.contains("problem") || content.contains("troubleshoot") ||
                           content.contains("fix") || content.contains("solution") ||
                           section.contains("troubleshooting") || section.contains("error");
                })
                .collect(Collectors.toList());
    }
    
    private List<RetrievalResult> filterForArchitecture(List<RetrievalResult> results, String query) {
        // Prioritize architecture and design-related content
        return results.stream()
                .filter(result -> {
                    String content = result.getContent().toLowerCase();
                    String section = result.getSection().toLowerCase();
                    
                    return content.contains("architecture") || content.contains("design") ||
                           content.contains("component") || content.contains("structure") ||
                           content.contains("pattern") || section.contains("architecture") ||
                           section.contains("design");
                })
                .collect(Collectors.toList());
    }
    
    private List<RetrievalResult> filterForVersionDiff(List<RetrievalResult> results, String query) {
        // Prioritize version and change-related content
        return results.stream()
                .filter(result -> {
                    String content = result.getContent().toLowerCase();
                    Map<String, Object> payload = result.getPayload();
                    
                    return content.contains("version") || content.contains("change") ||
                           content.contains("update") || content.contains("new") ||
                           content.contains("deprecated") || payload.containsKey("version");
                })
                .collect(Collectors.toList());
    }
    
    // Inner class to represent retrieval results with scores
    public static class RetrievalResult {
        private final String vectorId;
        private final Long chunkId;
        private final String content;
        private final String section;
        private final String chunkType;
        private final float score;
        private final Map<String, Object> payload;
        
        public RetrievalResult(String vectorId, Long chunkId, String content, String section, 
                             String chunkType, float score, Map<String, Object> payload) {
            this.vectorId = vectorId;
            this.chunkId = chunkId;
            this.content = content;
            this.section = section;
            this.chunkType = chunkType;
            this.score = score;
            this.payload = payload;
        }
        
        // Getters
        public String getVectorId() { return vectorId; }
        public Long getChunkId() { return chunkId; }
        public String getContent() { return content; }
        public String getSection() { return section; }
        public String getChunkType() { return chunkType; }
        public float getScore() { return score; }
        public Map<String, Object> getPayload() { return payload; }
    }
}
