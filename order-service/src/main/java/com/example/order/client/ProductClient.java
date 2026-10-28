package com.example.order.client;

import com.example.order.dto.external.ProductDetailDto;
import com.example.order.exception.DownstreamServiceException;
import com.example.order.exception.ProductNotFoundException;
import com.example.order.exception.ProductUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);

    private final RestClient productRestClient;

    public ProductClient(@Qualifier("productRestClient") RestClient productRestClient) {
        this.productRestClient = productRestClient;
    }

    public ProductDetailDto getProductById(Integer productId) {
        try {
            ProductDetailDto dto = productRestClient.get()
                    .uri("/api/v1/products/{id}", productId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        if (response.getStatusCode().value() == 404) {
                            throw new ProductNotFoundException(productId);
                        }
                        throw new HttpClientErrorException(response.getStatusCode(), response.getStatusText());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new DownstreamServiceException(
                                "product-service",
                                response.getStatusCode().value(),
                                "Product service error: " + response.getStatusText()
                        );
                    })
                    .body(ProductDetailDto.class);

            if (dto == null) {
                throw new ProductNotFoundException(productId);
            }

            if (Boolean.FALSE.equals(dto.active())) {
                throw new ProductUnavailableException(productId, "Product with ID " + productId + " is inactive");
            }

            return dto;
        } catch (ProductNotFoundException | ProductUnavailableException | DownstreamServiceException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.error("Network or timeout error calling product-service for productId={}: {}", productId, e.getMessage());
            throw new DownstreamServiceException("product-service", "Timeout or connection failure calling Product Service", e);
        } catch (HttpClientErrorException e) {
            log.error("Client error calling product-service for productId={}: status={}", productId, e.getStatusCode());
            throw new DownstreamServiceException("product-service", e.getStatusCode().value(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling product-service for productId={}: {}", productId, e.getMessage(), e);
            throw new DownstreamServiceException("product-service", "Unexpected failure calling Product Service", e);
        }
    }
}
