package com.dip.service;

import com.dip.domain.DocumentChunk;
import com.dip.domain.QueryAuditLog;
import com.dip.dto.QueryResponse;
import com.dip.repository.DocumentChunkRepository;
import com.dip.repository.QueryAuditLogRepository;
import io.qdrant.client.grpc.Points.ScoredPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RAGService {

    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^a-z0-9/._:-]+");
    
    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository chunkRepository;
    private final QueryAuditLogRepository auditRepository;
    private final ServiceRegistryService serviceRegistryService;
    private final LLMService llmService;
    private final VectorStoreService vectorStoreService;

    @Value("${retrieval.vector-limit:40}")
    private int vectorLimit;

    @Value("${retrieval.final-limit:5}")
    private int finalLimit;

    @Value("${retrieval.lexical-weight:0.35}")
    private float lexicalWeight;

    @Value("${retrieval.exact-match-boost:0.45}")
    private float exactMatchBoost;
    
    public QueryResponse query(String serviceCode, String query, String userId) 
            throws ExecutionException, InterruptedException {
        long startTime = System.currentTimeMillis();
        
        com.dip.domain.Service service = serviceRegistryService.getServiceByCode(serviceCode);
        
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        
        List<ScoredPoint> candidatePoints = vectorStoreService.searchSimilarWithScores(
            queryEmbedding,
            service.getId(),
            vectorLimit,
            null
        );

        List<Long> chunkIds = candidatePoints.stream()
                .filter(point -> point.getPayloadMap().containsKey("chunk_id"))
                .map(point -> Long.parseLong(point.getPayloadMap().get("chunk_id").getStringValue()))
                .toList();

        Map<Long, DocumentChunk> chunkById = chunkRepository.findAllById(chunkIds).stream()
                .collect(Collectors.toMap(DocumentChunk::getId, chunk -> chunk));

        List<DocumentChunk> relevantChunks = candidatePoints.stream()
                .map(point -> toRankedChunk(query, point, chunkById))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RankedChunk::score).reversed())
                .limit(finalLimit)
                .map(RankedChunk::chunk)
                .toList();
        
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

    private RankedChunk toRankedChunk(String query, ScoredPoint point, Map<Long, DocumentChunk> chunkById) {
        if (!point.getPayloadMap().containsKey("chunk_id")) {
            return null;
        }

        long chunkId = Long.parseLong(point.getPayloadMap().get("chunk_id").getStringValue());
        DocumentChunk chunk = chunkById.get(chunkId);
        if (chunk == null || chunk.getContent() == null) {
            return null;
        }

        double score = hybridScore(query, chunk.getContent(), point.getScore());
        return new RankedChunk(chunk, score);
    }

    private double hybridScore(String query, String content, float semanticScore) {
        String normalizedQuery = normalize(query);
        String normalizedContent = normalize(content);
        double lexicalScore = lexicalScore(normalizedQuery, normalizedContent);
        double score = ((1.0d - lexicalWeight) * semanticScore) + (lexicalWeight * lexicalScore);
        if (!normalizedQuery.isBlank() && normalizedContent.contains(normalizedQuery)) {
            score += exactMatchBoost;
        }
        return score;
    }

    private double lexicalScore(String query, String content) {
        if (query.isBlank() || content.isBlank()) {
            return 0.0d;
        }

        String[] tokens = TOKEN_SPLIT_PATTERN.split(query);
        int tokenHits = 0;
        int significantTokens = 0;

        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            significantTokens++;
            if (content.contains(token)) {
                tokenHits++;
            }
        }

        if (significantTokens == 0) {
            return 0.0d;
        }

        double coverage = (double) tokenHits / (double) significantTokens;
        double phraseBonus = content.contains(query) ? 1.0d : longestLiteralBonus(query, content);
        return Math.min(1.0d, (coverage * 0.7d) + (phraseBonus * 0.3d));
    }

    private double longestLiteralBonus(String query, String content) {
        if (query.length() < 4) {
            return 0.0d;
        }

        for (int length = query.length(); length >= 4; length--) {
            for (int index = 0; index + length <= query.length(); index++) {
                String candidate = query.substring(index, index + length).trim();
                if (candidate.length() >= 4 && content.contains(candidate)) {
                    return Math.min(1.0d, (double) candidate.length() / (double) query.length());
                }
            }
        }
        return 0.0d;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().trim();
    }

    private record RankedChunk(DocumentChunk chunk, double score) {
    }
}
