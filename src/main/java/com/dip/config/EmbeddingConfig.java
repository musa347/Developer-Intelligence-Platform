package com.dip.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class EmbeddingConfig {

    @Value("${embedding.provider:onnx}")
    private String provider;

    @Value("${embedding.model-path:models/all-MiniLM-L6-v2/model.onnx}")
    private String modelPath;

    @Value("${embedding.tokenizer-path:models/all-MiniLM-L6-v2/tokenizer.json}")
    private String tokenizerPath;

    @Value("${embedding.dimension:384}")
    private int dimension;

    @Value("${embedding.max-tokens:256}")
    private int maxTokens;

    @Value("${embedding.normalize:true}")
    private boolean normalize;

    public String getProvider() {
        return provider;
    }

    public Path getModelPath() {
        return Path.of(modelPath);
    }

    public Path getTokenizerPath() {
        return Path.of(tokenizerPath);
    }

    public int getDimension() {
        return dimension;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public boolean isNormalize() {
        return normalize;
    }
}
