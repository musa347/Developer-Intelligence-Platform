package com.dip.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QdrantConfig {
    
    @Value("${qdrant.host}")
    private String host;
    
    @Value("${qdrant.port}")
    private int port;
    
    @Value("${qdrant.api-key:}")
    private String apiKey;
    
    @Value("${qdrant.use-tls:false}")
    private boolean useTls;
    
    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(host, port, useTls);
        
        if (apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-api-key-here")) {
            builder.withApiKey(apiKey);
        }
        
        return new QdrantClient(builder.build());
    }
}
