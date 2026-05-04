package com.dip.orchestration;

import com.dip.domain.DocumentChunk;
import com.dip.domain.QueryIntent;
import com.dip.repository.QueryAuditLogRepository;
import com.dip.service.IntentClassifier;
import com.dip.service.ServiceRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryOrchestrator {
    
    private final IntentClassifier intentClassifier;
    private final RetrievalStrategyResolver strategyResolver;
    private final RetrievalEngine retrievalEngine;
    private final AnswerComposer answerComposer;
    private final ConfidenceEvaluator confidenceEvaluator;
    private final ServiceRegistryService serviceRegistryService;
    private final QueryAuditLogRepository auditLogRepository;
    
    public QueryResponse execute(QueryRequest request) throws ExecutionException, InterruptedException {
        long startTime = System.currentTimeMillis();

        QueryIntent intent = intentClassifier.classifyIntent(request.getQuery());
        log.info("Query intent classified as: {}", intent);

        RetrievalStrategy strategy = strategyResolver.resolve(intent);
        int topK = strategyResolver.getTopK(strategy);
        log.info("Using retrieval strategy: {} with topK={}", strategy, topK);

        var service = serviceRegistryService.getServiceByCode(request.getServiceCode());
        List<DocumentChunk> chunks = retrievalEngine.retrieveAsChunks(
                request.getQuery(), 
                service.getId(), 
                strategy,
                topK
        );
        log.info("Retrieved {} chunks", chunks.size());

        String answer = answerComposer.compose(request.getQuery(), chunks, service.getName());
        String confidence = confidenceEvaluator.evaluate(chunks, strategy);

        long responseTime = System.currentTimeMillis() - startTime;
        auditQuery(request, service, intent, confidence, responseTime);
        
        return QueryResponse.builder()
                .answer(answer)
                .chunks(chunks)
                .confidence(confidence)
                .intent(intent)
                .strategy(strategy)
                .build();
    }
    
    private void auditQuery(QueryRequest request, com.dip.domain.Service service, QueryIntent intent, String confidence, long responseTime) {
        var auditLog = new com.dip.domain.QueryAuditLog();
        auditLog.setService(service);
        auditLog.setQuery(request.getQuery());
        auditLog.setIntent(intent);
        auditLog.setUserId(request.getUserId());
        auditLog.setLatencyMs(responseTime);
        auditLogRepository.save(auditLog);
    }
}
