package com.dip.service;

import com.dip.config.EmbeddingConfig;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private final EmbeddingBackend embeddingBackend;

    public EmbeddingService(EmbeddingConfig embeddingConfig, OnnxEmbeddingBackend onnxEmbeddingBackend) {
        this.embeddingBackend = switch (embeddingConfig.getProvider().toLowerCase()) {
            case "onnx" -> onnxEmbeddingBackend;
            default -> throw new IllegalStateException("Unsupported embedding provider: " + embeddingConfig.getProvider());
        };
    }

    public float[] generateEmbedding(String text) {
        return embeddingBackend.generateEmbedding(text);
    }

    public int getEmbeddingDimension() {
        return embeddingBackend.dimension();
    }
}
