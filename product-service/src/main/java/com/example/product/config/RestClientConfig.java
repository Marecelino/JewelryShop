package com.example.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cấu hình RestClient để gọi Inventory Service.
 *
 * <p>Spring Boot 4 (Spring Framework 6.1+) cung cấp {@link RestClient} là HTTP client
 * đồng bộ hiện đại, thay thế cho RestTemplate. RestClient phù hợp với ứng dụng
 * Spring MVC (Servlet) như product-service; không cần WebFlux.</p>
 *
 * <p>Timeout được đặt để tránh blocking thread vô thời hạn khi Inventory Service
 * không phản hồi. Khi timeout xảy ra, {@link org.springframework.web.client.ResourceAccessException}
 * sẽ được ném và xử lý ở tầng Service hoặc GlobalExceptionHandler.</p>
 */
@Configuration
public class RestClientConfig {

    /** URL cơ sở của Inventory Service, được đọc từ application.properties. */
    @Value("${inventory.service.url}")
    private String inventoryServiceUrl;

    /**
     * Bean RestClient được inject vào {@code InventoryClient}.
     *
     * <p>Cấu hình timeout:
     * <ul>
     *   <li>Connect timeout 3 giây: thời gian thiết lập TCP connection</li>
     *   <li>Read timeout 5 giây: thời gian chờ Inventory Service trả response body</li>
     * </ul>
     * </p>
     */
    @Bean
    public RestClient inventoryRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);

        return RestClient.builder()
                .baseUrl(inventoryServiceUrl)
                .requestFactory(factory)
                .build();
    }
}
