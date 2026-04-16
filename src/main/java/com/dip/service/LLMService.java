package com.dip.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.util.Map;

@Service
public class LLMService {
    
    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String model;
    
    public LLMService(
        @Value("${ollama.api.url}") String apiUrl,
        @Value("${ollama.chat.model}") String model,
        @Value("${ollama.timeout.connect:5}") int connectTimeout,
        @Value("${ollama.timeout.read:60}") int readTimeout,
        RestTemplateBuilder builder
    ) {
        this.apiUrl = apiUrl;
        this.model = model;
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(connectTimeout))
            .setReadTimeout(Duration.ofSeconds(readTimeout))
            .build();
    }
    
    public String generateAnswer(String serviceName, String query, String context) {
        if (context.isEmpty()) {
            return "This information is not documented in the available documentation for " + serviceName + ".";
        }
        
        String prompt = String.format(
            "Answer this question using ONLY the context below. Be brief.\n\nContext: %s\n\nQuestion: %s\n\nAnswer:",
            context, query
        );
        
        Map<String, Object> requestBody = Map.of(
            "model", model,
            "prompt", prompt,
            "stream", false,
            "options", Map.of("num_predict", 100)
        );
        
        try {
            Map<String, Object> response = restTemplate.postForObject(
                apiUrl + "/api/generate",
                requestBody,
                Map.class
            );
            
            return response != null ? (String) response.get("response") : "No response from LLM";
        } catch (Exception e) {
            return "Error: LLM timeout or unavailable. Try a simpler query or check if Ollama is running.";
        }
    }
}
