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
    
    @Value("${ollama.timeout.connect:5}")
    private int connectTimeout;
    
    @Value("${ollama.timeout.read:30}")
    private int readTimeout;
    
    @Value("${ollama.timeout.write:30}")
    private int writeTimeout;
    
    @Bean
    public WebClient ollamaWebClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout * 1000)
            .responseTimeout(Duration.ofSeconds(readTimeout))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(writeTimeout, TimeUnit.SECONDS)));
        
        return WebClient.builder()
            .baseUrl(ollamaUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
