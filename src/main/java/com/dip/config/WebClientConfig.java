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
    
    @Value("${qdrant.host}")
    private String qdrantHost;
    
    @Value("${qdrant.port}")
    private int qdrantPort;
    
    @Value("${qdrant.use-tls}")
    private boolean qdrantUseTls;
    
    @Value("${llm.chat.url}")
    private String llmUrl;
    
    @Value("${llm.timeout.connect:10}")
    private int llmConnectTimeout;
    
    @Value("${llm.timeout.read:30}")
    private int llmReadTimeout;
    
    @Bean
    public WebClient llmWebClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, llmConnectTimeout * 1000)
            .responseTimeout(Duration.ofSeconds(llmReadTimeout))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(llmReadTimeout, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(llmReadTimeout, TimeUnit.SECONDS)));
        
        return WebClient.builder()
            .baseUrl(llmUrl)
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
