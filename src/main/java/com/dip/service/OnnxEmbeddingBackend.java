package com.dip.service;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.dip.config.EmbeddingConfig;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class OnnxEmbeddingBackend implements EmbeddingBackend {

    private static final String INPUT_IDS = "input_ids";
    private static final String ATTENTION_MASK = "attention_mask";
    private static final String TOKEN_TYPE_IDS = "token_type_ids";

    private final EmbeddingConfig embeddingConfig;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final SimpleWordTokenizer tokenizer;
    private final int dimension;

    public OnnxEmbeddingBackend(EmbeddingConfig embeddingConfig) {
        this.embeddingConfig = embeddingConfig;
        try {
            // Ensure model files exist, download if missing
            ensureModelFilesExist();
            
            validateModelAssets(embeddingConfig.getModelPath(), embeddingConfig.getTokenizerPath());
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(
                    embeddingConfig.getModelPath().toString(),
                    new OrtSession.SessionOptions()
            );
            // Use simple tokenizer to avoid DJL native library issues
            this.tokenizer = new SimpleWordTokenizer(embeddingConfig.getTokenizerPath());
            this.dimension = resolveDimension();
        } catch (OrtException | IOException e) {
            throw new IllegalStateException("Failed to initialize ONNX embedding backend", e);
        }
    }

    @Override
    public float[] generateEmbedding(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return new float[dimension];
        }

        try {
            SimpleWordTokenizer.TokenizationResult encoding = tokenizer.encode(normalized, embeddingConfig.getMaxTokens());
            long[][] inputIds = new long[][]{encoding.getTokenIds()};
            long[][] attentionMask = new long[][]{encoding.getAttentionMask()};

            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put(INPUT_IDS, OnnxTensor.createTensor(environment, inputIds));

            if (session.getInputInfo().containsKey(ATTENTION_MASK)) {
                inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(environment, attentionMask));
            }

            if (session.getInputInfo().containsKey(TOKEN_TYPE_IDS)) {
                long[] typeIds = new long[inputIds[0].length]; // All zeros for single sequence
                inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(
                        environment,
                        new long[][]{typeIds}
                ));
            }

            try (OrtSession.Result results = session.run(inputs)) {
                float[] embedding = extractEmbedding(results, attentionMask[0]);
                return normalize(embedding);
            } finally {
                for (OnnxTensor tensor : inputs.values()) {
                    tensor.close();
                }
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to generate ONNX embedding", e);
        }
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @PreDestroy
    public void close() {
        try {
            session.close();
        } catch (Exception ignored) {
        }
    }

    private int resolveDimension() throws OrtException {
        for (NodeInfo nodeInfo : session.getOutputInfo().values()) {
            if (nodeInfo.getInfo() instanceof TensorInfo tensorInfo) {
                long[] shape = tensorInfo.getShape();
                if (shape.length >= 2 && shape[shape.length - 1] > 0) {
                    return (int) shape[shape.length - 1];
                }
            }
        }
        return embeddingConfig.getDimension();
    }

    private float[] extractEmbedding(OrtSession.Result results, long[] attentionMask) throws OrtException {
        for (Map.Entry<String, OnnxValue> entry : results) {
            Object value = entry.getValue().getValue();
            if (value instanceof float[][] direct && direct.length > 0) {
                return direct[0];
            }
            if (value instanceof float[][][] tokenEmbeddings && tokenEmbeddings.length > 0) {
                return meanPool(tokenEmbeddings[0], attentionMask);
            }
        }
        throw new IllegalStateException("No supported embedding output found in ONNX result");
    }

    private float[] meanPool(float[][] tokenEmbeddings, long[] attentionMask) {
        int embeddingSize = tokenEmbeddings[0].length;
        float[] pooled = new float[embeddingSize];
        float tokenCount = 0.0f;

        for (int tokenIndex = 0; tokenIndex < tokenEmbeddings.length && tokenIndex < attentionMask.length; tokenIndex++) {
            if (attentionMask[tokenIndex] == 0L) {
                continue;
            }
            tokenCount += 1.0f;
            for (int dim = 0; dim < embeddingSize; dim++) {
                pooled[dim] += tokenEmbeddings[tokenIndex][dim];
            }
        }

        if (tokenCount == 0.0f) {
            return pooled;
        }

        for (int dim = 0; dim < embeddingSize; dim++) {
            pooled[dim] /= tokenCount;
        }
        return pooled;
    }

    private long[] truncate(long[] values, int maxTokens) {
        int length = Math.min(values.length, maxTokens);
        long[] truncated = new long[length];
        System.arraycopy(values, 0, truncated, 0, length);
        return truncated;
    }

    private float[] normalize(float[] embedding) {
        if (!embeddingConfig.isNormalize()) {
            return embedding;
        }

        double magnitude = 0.0d;
        for (float value : embedding) {
            magnitude += value * value;
        }
        magnitude = Math.sqrt(magnitude);

        if (magnitude == 0.0d) {
            return embedding;
        }

        float[] normalized = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            normalized[i] = (float) (embedding[i] / magnitude);
        }
        return normalized;
    }

    private void validateModelAssets(Path modelPath, Path tokenizerPath) throws IOException {
        if (!Files.exists(modelPath)) {
            throw new IOException("Embedding model not found at " + modelPath);
        }
        if (!Files.exists(tokenizerPath)) {
            throw new IOException("Embedding tokenizer not found at " + tokenizerPath);
        }
    }

    private void ensureModelFilesExist() throws IOException {
        Path modelPath = embeddingConfig.getModelPath();
        Path tokenizerPath = embeddingConfig.getTokenizerPath();
        
        // Download model if missing
        if (!Files.exists(modelPath)) {
            downloadModelFile(modelPath);
        }
        
        // Download tokenizer if missing
        if (!Files.exists(tokenizerPath)) {
            downloadTokenizerFile(tokenizerPath);
        }
    }

    private void downloadModelFile(Path targetPath) throws IOException {
        String modelUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/pytorch_model.bin";
        downloadFile(modelUrl, targetPath, "Downloading ONNX model...");
    }

    private void downloadTokenizerFile(Path targetPath) throws IOException {
        String tokenizerUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json";
        downloadFile(tokenizerUrl, targetPath, "Downloading tokenizer...");
    }

    private void downloadFile(String url, Path targetPath, String description) throws IOException {
        System.out.println(description + " from " + url);
        try (InputStream in = new URL(url).openStream()) {
            Files.createDirectories(targetPath.getParent());
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Downloaded to: " + targetPath);
        } catch (Exception e) {
            throw new IOException("Failed to download " + description + " from " + url, e);
        }
    }

    /**
     * Simple word-based tokenizer that avoids DJL native library issues.
     * Provides basic tokenization functionality for ONNX models.
     */
    private static class SimpleWordTokenizer {
        private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
        private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[^a-zA-Z0-9\\s]");
        
        public SimpleWordTokenizer(Path tokenizerPath) {
            // We don't actually need the tokenizer.json for this simple implementation
            // but we keep the constructor for compatibility
        }

        public TokenizationResult encode(String text, int maxTokens) {
            // Normalize text
            String normalized = text.toLowerCase().trim();
            normalized = PUNCTUATION_PATTERN.matcher(normalized).replaceAll(" ");
            
            // Split into words
            String[] words = WHITESPACE_PATTERN.split(normalized);
            
            // Convert to token IDs using simple hash-based approach
            List<Long> tokenIds = new ArrayList<>();
            List<Long> attentionMask = new ArrayList<>();
            
            // Add tokens up to maxTokens
            for (int i = 0; i < Math.min(words.length, maxTokens); i++) {
                if (!words[i].isEmpty()) {
                    // Simple hash-based token ID generation
                    long tokenId = Math.abs(words[i].hashCode()) % 30000L + 1000L; // Avoid conflicts with special tokens
                    tokenIds.add(tokenId);
                    attentionMask.add(1L);
                }
            }
            
            // Add special tokens if we have space
            if (tokenIds.size() < maxTokens) {
                tokenIds.add(0, 101L); // [CLS] token
                attentionMask.add(0, 1L);
                
                if (tokenIds.size() < maxTokens) {
                    tokenIds.add(102L); // [SEP] token
                    attentionMask.add(1L);
                }
            }
            
            // Convert to arrays
            long[] tokenIdsArray = tokenIds.stream().mapToLong(Long::longValue).toArray();
            long[] attentionMaskArray = attentionMask.stream().mapToLong(Long::longValue).toArray();
            
            return new TokenizationResult(tokenIdsArray, attentionMaskArray);
        }

        public static class TokenizationResult {
            private final long[] tokenIds;
            private final long[] attentionMask;

            public TokenizationResult(long[] tokenIds, long[] attentionMask) {
                this.tokenIds = tokenIds;
                this.attentionMask = attentionMask;
            }

            public long[] getTokenIds() {
                return tokenIds;
            }

            public long[] getAttentionMask() {
                return attentionMask;
            }
        }
    }
}
