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
    
    private final WebClient huggingfaceWebClient;
    
    @Value("${huggingface.api.key:}")
    private String huggingfaceApiKey;
    
    @Value("${huggingface.embedding.model}")
    private String embeddingModel;
    
    public float[] generateEmbedding(String text) {
        return generateEmbeddingWithHuggingFace(text);
    }
    
    @SuppressWarnings("unchecked")
    private float[] generateEmbeddingWithHuggingFace(String text) {
        Map<String, Object> response = (Map<String, Object>) huggingfaceWebClient.post()
            .uri("/" + embeddingModel)
            .header("Authorization", "Bearer " + huggingfaceApiKey)
            .bodyValue(Map.of(
                "inputs", text
            ))
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(30))
            .block();
        
        List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
        List<Double> embedding = embeddings.get(0);
        
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
