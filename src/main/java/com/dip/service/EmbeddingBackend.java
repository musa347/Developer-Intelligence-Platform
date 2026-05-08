package com.dip.service;

public interface EmbeddingBackend {
    float[] generateEmbedding(String text);
    int dimension();
}
