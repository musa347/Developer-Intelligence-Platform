package com.dip.service;

import com.dip.domain.QueryIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IntentClassifier {
    
    private final WebClient ollamaWebClient;
    
    public QueryIntent classifyIntent(String query) {
        String prompt = """
            Classify this query into ONE category:
            - CONCEPTUAL_EXPLANATION: "How does X work?", "What is X?"
            - ARCHITECTURAL_OVERVIEW: "What are the components?", "System architecture"
            - ENDPOINT_LOOKUP: "What endpoint...", "API for..."
            - INTEGRATION_GUIDANCE: "How to integrate", "How to call"
            - ERROR_EXPLANATION: "What does error X mean?", "Error code"
            - CHANGE_HISTORY: "What changed in version", "Differences between"
            - LOG_ANALYSIS: "Analyze this log", "What does this log mean"
            
            Query: %s
            
            Respond with ONLY the category name.
            """.formatted(query);
        
        try {
            Map<String, Object> response = ollamaWebClient.post()
                .uri("/api/generate")
                .bodyValue(Map.of(
                    "model", "qwen2.5:1.5b",
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of("temperature", 0)
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            String intent = ((String) response.get("response")).trim();
            return QueryIntent.valueOf(intent);
        } catch (Exception e) {
            return QueryIntent.CONCEPTUAL_EXPLANATION;
        }
    }
}
