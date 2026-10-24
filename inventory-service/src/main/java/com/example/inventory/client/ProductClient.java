package com.example.inventory.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Component
public class ProductClient {

    private final RestClient productRestClient;

    public ProductClient(RestClient productRestClient) {
        this.productRestClient = productRestClient;
    }

    public boolean existsById(Integer productId) {
        try {
            productRestClient.head()
                    .uri("/api/v1/products/{id}", productId)
                    .retrieve()
                    .toBodilessEntity();

            return true;

        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return false;
            }

            throw ex;
        }
    }
}