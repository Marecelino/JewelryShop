package com.example.product.client;

import com.example.product.dto.external.InventorySummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HTTP client gọi Inventory Service để lấy thông tin tồn kho.
 *
 * <p>Class này là cầu nối duy nhất giữa Product Service và Inventory Service.
 * Không gọi HTTP từ Controller, Service hoặc Mapper; chỉ gọi thông qua class này.</p>
 *
 * <p><b>Chính sách lỗi:</b>
 * <ul>
 *   <li>{@link ResourceAccessException}: mạng không kết nối được hoặc timeout.
 *       Ném ngoại lệ để Service xử lý — không trả số 0 giả.</li>
 *   <li>{@link RestClientResponseException}: Inventory Service trả HTTP lỗi (4xx/5xx).
 *       Ném ngoại lệ để Service xử lý.</li>
 * </ul>
 * </p>
 */
@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private static final String SUMMARIES_PATH = "/api/v1/inventory-summaries";

    private final RestClient restClient;

    public InventoryClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    /**
     * Lấy tổng hợp tồn kho cho nhiều sản phẩm trong <b>một request duy nhất</b>.
     *
     * <p>Inventory Service hỗ trợ nhận nhiều {@code productIds} qua query parameter
     * lặp lại ({@code ?productIds=1&productIds=3&productIds=4}). RestClient xây URL
     * đúng định dạng này thông qua {@code .param("productIds", productIds)}.</p>
     *
     * <p>Nếu một productId không có inventory item nào trong Inventory Service,
     * service đó tự trả về {@code availableQuantity=0} — đây là dữ liệu hợp lệ.
     * Client code không cần phân biệt "product chưa có hàng" với "lỗi kết nối"
     * vì method này sẽ ném exception nếu có lỗi upstream.</p>
     *
     * @param productIds danh sách productId cần tra cứu
     * @return map từ productId sang InventorySummaryDto (chỉ chứa các ID được trả về)
     * @throws ResourceAccessException nếu không kết nối được hoặc timeout
     * @throws RestClientResponseException nếu Inventory Service trả HTTP lỗi
     */
    public Map<Integer, InventorySummaryDto> getSummariesByProductIds(
            Collection<Integer> productIds) {

        log.debug("Fetching inventory summaries for productIds: {}", productIds);

        List<InventorySummaryDto> summaries = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(SUMMARIES_PATH)
                        .queryParam("productIds", productIds)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (summaries == null) {
            log.warn("Inventory Service returned null body for productIds: {}", productIds);
            return Map.of();
        }

        return summaries.stream()
                .collect(Collectors.toMap(
                        InventorySummaryDto::productId,
                        Function.identity()
                ));
    }

    /**
     * Lấy tổng hợp tồn kho cho một sản phẩm duy nhất.
     *
     * <p>Gọi nội bộ {@link #getSummariesByProductIds(Collection)} và trả về
     * {@link Optional} rỗng nếu Inventory không có dữ liệu cho productId này.</p>
     *
     * @param productId ID của sản phẩm
     * @return Optional chứa summary nếu Inventory Service có dữ liệu
     * @throws ResourceAccessException nếu không kết nối được hoặc timeout
     * @throws RestClientResponseException nếu Inventory Service trả HTTP lỗi
     */
    public Optional<InventorySummaryDto> getSummaryByProductId(Integer productId) {
        Map<Integer, InventorySummaryDto> result =
                getSummariesByProductIds(List.of(productId));
        return Optional.ofNullable(result.get(productId));
    }
}
