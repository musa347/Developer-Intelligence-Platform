package com.dip.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class LLMService {
    
    private final RestTemplate restTemplate;
    private final String groqApiUrl;
    private final String groqApiKey;
    private final String groqModel;
    
    public LLMService(
        @Value("${groq.api.url}") String groqApiUrl,
        @Value("${groq.api.key:}") String groqApiKey,
        @Value("${groq.chat.model}") String groqModel,
        @Value("${groq.timeout.connect:10}") int groqConnectTimeout,
        @Value("${groq.timeout.read:30}") int groqReadTimeout,
        RestTemplateBuilder builder
    ) {
        this.groqApiUrl = groqApiUrl;
        this.groqApiKey = groqApiKey;
        this.groqModel = groqModel;
        
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(groqConnectTimeout))
            .setReadTimeout(Duration.ofSeconds(groqReadTimeout))
            .build();
    }
    
    public String generateAnswer(String serviceName, String query, String context) {
        if (context.isEmpty()) {
            return "This information is not documented in the available documentation for " + serviceName + ".";
        }
        
        return generateAnswerWithGroq(serviceName, query, context);
    }
    
    private String generateAnswerWithGroq(String serviceName, String query, String context) {
        String prompt = String.format(
            "Answer this question using ONLY the context below. Be brief and concise.\n\nContext: %s\n\nQuestion: %s\n\nAnswer:",
            context, query
        );
        
        Map<String, Object> requestBody = Map.of(
            "model", groqModel,
            "messages", List.of(
                Map.of("role", "user", "content", prompt)
            ),
            "max_tokens", 150,
            "temperature", 0.1
        );
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            Map<String, Object> response = restTemplate.postForObject(
                groqApiUrl,
                entity,
                Map.class
            );
            
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    return (String) message.get("content");
                }
            }
            
            return "No response from Groq";
        } catch (Exception e) {
            return "Error: Groq API unavailable. Please check your API key or try again.";
        }
    }
    
    }
