package com.dip.orchestration;

import com.dip.domain.DocumentChunk;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConfidenceEvaluator {
    
    public String evaluate(List<DocumentChunk> chunks, RetrievalStrategy strategy) {
        if (chunks.isEmpty()) {
            return "low";
        }
        
        int chunkCount = chunks.size();
        boolean hasMultipleSources = chunks.stream()
                .map(c -> c.getArtifact().getId())
                .distinct()
                .count() > 1;
        
        int score = 0;
        
        // Chunk count scoring
        if (chunkCount >= 3) score += 30;
        else if (chunkCount >= 2) score += 20;
        else score += 10;
        
        // Multiple sources boost
        if (hasMultipleSources) score += 20;
        
        // Strategy-specific scoring
        score += switch (strategy) {
            case ENDPOINT_DRILLDOWN -> 30; // High confidence for structured data
            case ARCHITECTURE -> 20;
            case TROUBLESHOOTING -> 15;
            case DOCUMENTATION -> 20;
            case VERSION_DIFF -> 10;
        };
        
        // Content quality check
        long avgContentLength = (long) chunks.stream()
                .mapToInt(c -> c.getContent().length())
                .average()
                .orElse(0);
        
        if (avgContentLength > 200) score += 20;
        else if (avgContentLength > 100) score += 10;
        
        if (score >= 70) return "high";
        if (score >= 40) return "medium";
        return "low";
    }
}
