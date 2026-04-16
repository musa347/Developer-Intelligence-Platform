package com.dip.orchestration;

import com.dip.domain.ChunkType;
import com.dip.domain.DocumentChunk;
import com.dip.repository.DocumentChunkRepository;
import com.dip.service.EmbeddingService;
import com.dip.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class RetrievalEngine {
    
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final DocumentChunkRepository chunkRepository;
    
    public List<DocumentChunk> retrieve(
            String query, 
            Long serviceId, 
            RetrievalStrategy strategy,
            int topK) throws ExecutionException, InterruptedException {
        
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        ChunkType filterType = getChunkTypeFilter(strategy);
        
        List<String> chunkIds = vectorStoreService.searchSimilar(
                queryEmbedding, 
                serviceId, 
                topK,
                filterType
        );
        
        return chunkRepository.findAllById(
                chunkIds.stream().map(Long::parseLong).toList()
        );
    }
    
    private ChunkType getChunkTypeFilter(RetrievalStrategy strategy) {
        return null;
    }
}
