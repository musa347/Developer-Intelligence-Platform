package com.dip.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QdrantInitializationService {
    
    private final WebClient qdrantWebClient;
    private final EmbeddingService embeddingService;

    @Value("${qdrant.collection-name}")
    private String collectionName;
    
    @Value("${qdrant.api-key}")
    private String apiKey;
    
    @EventListener(ApplicationReadyEvent.class)
    public void initializeQdrantCollection() {
        log.info("Initializing Qdrant collection: {}", collectionName);
        
        try {
            Thread.sleep(1000);

            if (collectionExists()) {
                log.info("Qdrant collection '{}' already exists", collectionName);
            } else {
                log.info("Creating Qdrant collection '{}'", collectionName);
                createCollection();
                log.info("Qdrant collection '{}' created successfully", collectionName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize Qdrant collection '{}'", collectionName, e);
        }
    }
    
    private boolean collectionExists() {
        try {
            Map<String, Object> response = qdrantWebClient.get()
                .uri("/collections/" + collectionName)
                .header("api-key", apiKey)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            return response != null && response.containsKey("result");
        } catch (Exception e) {
            log.debug("Collection '{}' does not exist or is not accessible", collectionName);
            return false;
        }
    }
    
    private void createCollection() {
        Map<String, Object> vectorsConfig = Map.of(
            "size", embeddingService.getEmbeddingDimension(),
            "distance", "Cosine"
        );
        
        Map<String, Object> createRequest = Map.of(
            "vectors", vectorsConfig
        );
        
        try {
            Map<String, Object> response = qdrantWebClient.put()
                .uri("/collections/" + collectionName)
                .header("api-key", apiKey)
                .bodyValue(createRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response == null || !response.containsKey("result")) {
                throw new RuntimeException("Failed to create Qdrant collection");
            }
        } catch (Exception e) {
            if (e.getMessage().contains("application/grpc") || e.getMessage().contains("200 OK")) {
                log.info("Qdrant collection creation returned grpc response, checking if collection exists");
                try {
                    Thread.sleep(500); // Give it a moment to complete
                    if (collectionExists()) {
                        log.info("Qdrant collection '{}' was created successfully", collectionName);
                        return;
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            log.warn("Failed to create Qdrant collection, but it may have been created by another process: {}", e.getMessage());
        }
    }
}
