package com.dip.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmbeddingService {
    
    private final WebClient embeddingWebClient;
    
    @Value("${embedding.api.key:}")
    private String embeddingApiKey;
    
    @Value("${embedding.model.name}")
    private String embeddingModel;
    
    public float[] generateEmbedding(String text) {
        return generateEmbeddingWithOpenAI(text);
    }
    
    @SuppressWarnings("unchecked")
    private float[] generateEmbeddingWithOpenAI(String text) {
        Map<String, Object> requestBody = Map.of(
            "model", embeddingModel,
            "input", text
        );
        
        Map<String, Object> response = (Map<String, Object>) embeddingWebClient.post()
            .uri("/embeddings")
            .header("Authorization", "Bearer " + embeddingApiKey)
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(30))
            .block();
        
        List<List<Double>> data = (List<List<Double>>) response.get("data");
        List<Double> embedding = data.get(0);
        
        return convertToFloatArray(embedding);
    }
    
        
    private float[] convertToFloatArray(List<Double> embedding) {
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.get(i).floatValue();
        }
        return result;
    }
}
