package com.dip.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {
    
    @Value("${ollama.api.url}")
    private String ollamaUrl;
    
    @Value("${huggingface.api.url}")
    private String huggingfaceUrl;
    
    @Value("${qdrant.host}")
    private String qdrantHost;
    
    @Value("${qdrant.port}")
    private int qdrantPort;
    
    @Value("${qdrant.use-tls}")
    private boolean qdrantUseTls;
    
    @Value("${ollama.timeout.connect:5}")
    private int ollamaConnectTimeout;
    
    @Value("${ollama.timeout.read:30}")
    private int ollamaReadTimeout;
    
    @Value("${ollama.timeout.write:30}")
    private int ollamaWriteTimeout;
    
    @Value("${huggingface.timeout.connect:10}")
    private int huggingfaceConnectTimeout;
    
    @Value("${huggingface.timeout.read:30}")
    private int huggingfaceReadTimeout;
    
    @Bean
    public WebClient ollamaWebClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, ollamaConnectTimeout * 1000)
            .responseTimeout(Duration.ofSeconds(ollamaReadTimeout))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(ollamaReadTimeout, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(ollamaWriteTimeout, TimeUnit.SECONDS)));
        
        return WebClient.builder()
            .baseUrl(ollamaUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
    
    @Bean
    public WebClient huggingfaceWebClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, huggingfaceConnectTimeout * 1000)
            .responseTimeout(Duration.ofSeconds(huggingfaceReadTimeout))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(huggingfaceReadTimeout, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(huggingfaceReadTimeout, TimeUnit.SECONDS)));
        
        return WebClient.builder()
            .baseUrl(huggingfaceUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
    
    @Bean
    public WebClient qdrantWebClient() {
        String protocol = qdrantUseTls ? "https" : "http";
        String baseUrl = String.format("%s://%s:%d", protocol, qdrantHost, qdrantPort);
        
        return WebClient.builder()
            .baseUrl(baseUrl)
            .build();
    }
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
