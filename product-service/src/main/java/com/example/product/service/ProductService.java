package com.example.product.service;

import com.example.product.client.InventoryClient;
import com.example.product.dto.external.InventorySummaryDto;
import com.example.product.dto.request.CreateProductRequest;
import com.example.product.dto.request.UpdateProductRequest;
import com.example.product.dto.response.InventorySummary;
import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.Product;
import com.example.product.mapper.ProductMapper;
import com.example.product.repository.ProductRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service xử lý nghiệp vụ của Product Service.
 *
 * <p>Luồng đọc có bổ sung tồn kho:</p>
 * <ol>
 *   <li>Lấy dữ liệu Product từ database Product Service.</li>
 *   <li>Gọi Inventory Service <b>một lần batch</b> cho toàn bộ productId của kết quả.</li>
 *   <li>Ghép thông tin tồn kho vào từng {@link ProductResponse}.</li>
 * </ol>
 *
 * <p><b>Chính sách lỗi khi Inventory không khả dụng:</b>
 * Trường {@code inventory} trong response sẽ là {@code null}.
 * Service log cảnh báo và ném {@link ResponseStatusException} với status 503
 * để GlobalExceptionHandler trả lỗi rõ ràng cho client thay vì giả mạo
 * số lượng tồn kho bằng 0.</p>
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final InventoryClient inventoryClient;

    public ProductService(
            ProductRepository productRepository,
            ProductMapper productMapper,
            InventoryClient inventoryClient) {

        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.inventoryClient = inventoryClient;
    }

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    /** Lấy toàn bộ sản phẩm, bổ sung thông tin tồn kho bằng một lần gọi batch. */
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll();
        List<ProductResponse> responses = productMapper.toResponseList(products);
        enrichWithInventory(responses);
        return responses;
    }

    /** Lấy sản phẩm theo ID, bổ sung thông tin tồn kho. */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Integer productId) {
        Product product = findProductEntityById(productId);
        ProductResponse response = productMapper.toResponse(product);
        enrichWithInventory(List.of(response));
        return response;
    }

    /** Tìm sản phẩm theo category, bổ sung tồn kho batch. */
    @Transactional(readOnly = true)
    public List<ProductResponse> searchByCategory(String category) {
        List<Product> products = productRepository.findByCategory(category);
        List<ProductResponse> responses = productMapper.toResponseList(products);
        enrichWithInventory(responses);
        return responses;
    }

    /**
     * Lấy danh sách sản phẩm phân trang, bổ sung tồn kho chỉ cho trang hiện tại.
     *
     * <p>Gọi Inventory một lần cho các productId trong trang — tránh N+1 HTTP calls.</p>
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        Page<ProductResponse> page = productRepository.findAll(pageable)
                .map(productMapper::toResponse);
        enrichWithInventory(page.getContent());
        return page;
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    /**
     * Tạo sản phẩm mới.
     *
     * <p>Không tạo inventory item. Client phải gọi Inventory Service riêng
     * ({@code POST /api/v1/inventory-items}) để đăng ký từng món trang sức
     * vật lý sau khi sản phẩm được tạo.</p>
     *
     * <p>Response trả về thông tin sản phẩm vừa tạo. Trường {@code inventory}
     * sẽ là {@code null} vì chưa có inventory item nào được tạo.</p>
     */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = productMapper.toEntity(request);
        product.setCreatedAt(LocalDateTime.now());
        product.setActive(true);
        Product savedProduct = productRepository.save(product);
        // inventory = null vì sản phẩm mới chưa có inventory item
        return productMapper.toResponse(savedProduct);
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    /** Cập nhật thông tin sản phẩm. Không ảnh hưởng đến dữ liệu tồn kho. */
    @Transactional
    public ProductResponse updateProduct(
            Integer productId,
            UpdateProductRequest request) {

        Product existingProduct = findProductEntityById(productId);
        productMapper.updateEntity(existingProduct, request);
        Product updatedProduct = productRepository.save(existingProduct);
        ProductResponse response = productMapper.toResponse(updatedProduct);
        enrichWithInventory(List.of(response));
        return response;
    }

    // -------------------------------------------------------------------------
    // DELETE (soft delete)
    // -------------------------------------------------------------------------

    /**
     * Ngừng kinh doanh sản phẩm (soft delete).
     *
     * <p>Đặt {@code active = false} thay vì xóa vật lý để tránh các bản ghi
     * inventory trong Inventory Service trở thành orphan. Sản phẩm không hoạt động
     * sẽ không bị xóa khỏi database và có thể được kích hoạt lại.</p>
     *
     * <p>Không xóa inventory item. Inventory Service quản lý vòng đời của
     * từng món trang sức vật lý độc lập.</p>
     */
    @Transactional
    public void deleteProduct(Integer productId) {
        Product product = findProductEntityById(productId);
        product.setActive(false);
        productRepository.save(product);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Tìm Product Entity theo ID hoặc ném 404. */
    private Product findProductEntityById(Integer productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found: " + productId
                ));
    }

    /**
     * Bổ sung thông tin tồn kho vào danh sách response bằng <b>một lần gọi batch</b>.
     *
     * <p>Nếu Inventory Service không khả dụng hoặc trả lỗi, phương thức này ném
     * {@link ResponseStatusException} với status 503 thay vì để {@code inventory = null}
     * một cách im lặng. Client nhận được lỗi rõ ràng và không hiểu nhầm
     * {@code null} là "không có hàng".</p>
     *
     * <p>Nếu một productId không có inventory item nào, Inventory Service tự trả
     * {@code availableQuantity=0} — đây là dữ liệu hợp lệ, không phải lỗi.</p>
     *
     * @param responses danh sách ProductResponse cần bổ sung tồn kho
     */
    private void enrichWithInventory(Collection<ProductResponse> responses) {
        if (responses.isEmpty()) {
            return;
        }

        List<Integer> productIds = responses.stream()
                .map(ProductResponse::getProductId)
                .toList();

        Map<Integer, InventorySummaryDto> summaryMap;
        try {
            summaryMap = inventoryClient.getSummariesByProductIds(productIds);
        } catch (Exception ex) {
            // Inventory không khả dụng — log cảnh báo và ném 503
            log.warn("Inventory Service unavailable when enriching products {}: {}",
                    productIds, ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Inventory Service is currently unavailable. Please try again later."
            );
        }

        // Ghép inventory vào từng response theo productId
        Map<Integer, ProductResponse> responseById = responses.stream()
                .collect(Collectors.toMap(
                        ProductResponse::getProductId,
                        Function.identity()
                ));

        summaryMap.forEach((productId, dto) -> {
            ProductResponse response = responseById.get(productId);
            if (response != null) {
                response.setInventory(new InventorySummary(
                        dto.availableQuantity(),
                        dto.reservedQuantity(),
                        dto.soldQuantity(),
                        dto.damagedQuantity()
                ));
            }
        });
    }
}