package com.dip.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class LLMService {
    
    private final WebClient llmWebClient;
    private final String llmApiUrl;
    private final String llmApiKey;
    private final String llmModel;
    private final double temperature;
    private final int maxTokens;
    
    public LLMService(
        @Value("${llm.chat.url}") String llmApiUrl,
        @Value("${llm.api.key:}") String llmApiKey,
        @Value("${llm.chat.model}") String llmModel,
        @Value("${llm.temperature:0.1}") double temperature,
        @Value("${llm.max.tokens:512}") int maxTokens,
        WebClient llmWebClient
    ) {
        this.llmApiUrl = llmApiUrl;
        this.llmApiKey = llmApiKey;
        this.llmModel = llmModel;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.llmWebClient = llmWebClient;
    }
    
    public String generateAnswer(String serviceName, String query, String context) {
        System.out.println("[LLM DEBUG] Context length: " + context.length());
        System.out.println("[LLM DEBUG] Context is empty: " + context.isEmpty());
        System.out.println("[LLM DEBUG] Context preview: " + (context.length() > 200 ? context.substring(0, 200) + "..." : context));
        
        if (context.isEmpty()) {
            return "This information is not documented in the available documentation for " + serviceName + ".";
        }
        
        return generateAnswerWithLLM(serviceName, query, context);
    }
    
    private String generateAnswerWithLLM(String serviceName, String query, String context) {
        String prompt = String.format(
            "Answer this question using ONLY the context below. Be brief and concise.\n\nContext: %s\n\nQuestion: %s\n\nAnswer:",
            context, query
        );
        
        System.out.println("[LLM DEBUG] Prompt length: " + prompt.length());
        System.out.println("[LLM DEBUG] Full prompt: " + (prompt.length() > 500 ? prompt.substring(0, 500) + "..." : prompt));
        
        Map<String, Object> requestBody = Map.of(
            "model", llmModel,
            "messages", List.of(
                Map.of("role", "user", "content", prompt)
            ),
            "max_tokens", maxTokens,
            "temperature", temperature
        );
        
        try {
            Map<String, Object> response = (Map<String, Object>) llmWebClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + llmApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    return (String) message.get("content");
                }
            }
            
            return "No response from LLM";
        } catch (Exception e) {
            return "Error: LLM API unavailable. Please check your API key or try again.";
        }
    }
}
