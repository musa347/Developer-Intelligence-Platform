package com.dip.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmbeddingService {
    
    private final WebClient ollamaWebClient;
    
    public float[] generateEmbedding(String text) {
        Map<String, Object> response = ollamaWebClient.post()
            .uri("/api/embeddings")
            .bodyValue(Map.of(
                "model", "nomic-embed-text",
                "prompt", text
            ))
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(30))
            .block();
        
        List<Double> embedding = (List<Double>) response.get("embedding");
        
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.get(i).floatValue();
        }
        return result;
    }
}
