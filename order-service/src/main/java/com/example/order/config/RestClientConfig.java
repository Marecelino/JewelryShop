package com.example.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${product.service.url:http://localhost:8082}")
    private String productServiceUrl;

    @Value("${inventory.service.url:http://localhost:8081}")
    private String inventoryServiceUrl;

    @Value("${inventory.internal.api-key:}")
    private String inventoryInternalApiKey;

    @Value("${app.client.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${app.client.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }

    @Bean
    public RestClient productRestClient() {
        return RestClient.builder()
                .baseUrl(productServiceUrl)
                .requestFactory(createRequestFactory())
                .build();
    }

    @Bean
    public RestClient inventoryRestClient() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(inventoryServiceUrl)
                .requestFactory(createRequestFactory());

        if (inventoryInternalApiKey != null && !inventoryInternalApiKey.isBlank()) {
            builder.defaultHeader("X-Internal-Service-Key", inventoryInternalApiKey);
        }

        return builder.build();
    }
}
