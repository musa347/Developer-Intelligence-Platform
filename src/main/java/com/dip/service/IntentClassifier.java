package com.dip.service;

import com.dip.domain.QueryIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IntentClassifier {
    
    private final WebClient groqWebClient;
    
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
            Map<String, Object> response = groqWebClient.post()
                .bodyValue(Map.of(
                    "model", "llama-3.1-8b-instant",
                    "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0,
                    "max_tokens", 50
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String intent = ((String) message.get("content")).trim();
            return QueryIntent.valueOf(intent);
        } catch (Exception e) {
            return QueryIntent.CONCEPTUAL_EXPLANATION;
        }
    }
}
