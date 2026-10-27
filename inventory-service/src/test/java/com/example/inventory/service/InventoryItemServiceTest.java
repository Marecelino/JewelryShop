package com.example.inventory.service;

import com.example.inventory.client.ProductClient;
import com.example.inventory.dto.request.CreateInventoryItemRequest;
import com.example.inventory.dto.response.InventoryItemResponse;
import com.example.inventory.dto.response.InventorySummaryResponse;
import com.example.inventory.entity.InventoryItem;
import com.example.inventory.enums.InventoryStatus;
import com.example.inventory.exception.InventoryItemConflictException;
import com.example.inventory.exception.ProductNotFoundException;
import com.example.inventory.mapper.InventoryItemMapper;
import com.example.inventory.repository.InventoryItemRepository;
import com.example.inventory.repository.InventorySummaryProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryItemServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private InventoryItemMapper inventoryItemMapper;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private InventoryItemService inventoryItemService;

    // -------------------------------------------------------------------------
    // Test data builders
    // -------------------------------------------------------------------------

    private CreateInventoryItemRequest buildCreateRequest(Integer productId, String barcode, String serialNumber) {
        CreateInventoryItemRequest request = new CreateInventoryItemRequest();
        request.setProductId(productId);
        request.setBarcode(barcode);
        request.setSerialNumber(serialNumber);
        request.setActualWeightGrams(new BigDecimal("3.500"));
        request.setActualGoldPurityPercent(new BigDecimal("75.00"));
        request.setCertificateNumber("CERT-12345");
        request.setLocationCode("SHOWROOM-A01");
        return request;
    }

    private InventoryItem buildInventoryItem(Long id, Integer productId, String barcode, String serialNumber, InventoryStatus status) {
        InventoryItem item = new InventoryItem();
        item.setId(id);
        item.setProductId(productId);
        item.setBarcode(barcode);
        item.setSerialNumber(serialNumber);
        item.setActualWeightGrams(new BigDecimal("3.500"));
        item.setActualGoldPurityPercent(new BigDecimal("75.00"));
        item.setCertificateNumber("CERT-12345");
        item.setLocationCode("SHOWROOM-A01");
        item.setStatus(status);
        item.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
        item.setUpdatedAt(Instant.parse("2026-01-01T10:00:00Z"));
        return item;
    }

    private InventoryItemResponse buildInventoryItemResponse(Long id, Integer productId, String barcode, String serialNumber, InventoryStatus status) {
        return new InventoryItemResponse(
                id,
                productId,
                barcode,
                serialNumber,
                new BigDecimal("3.500"),
                new BigDecimal("75.00"),
                "CERT-12345",
                "SHOWROOM-A01",
                status,
                null,
                null,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:00Z")
        );
    }

    private InventorySummaryProjection buildMockProjection(
            Integer productId,
            Long available,
            Long reserved,
            Long sold,
            Long damaged
    ) {
        InventorySummaryProjection projection = mock(InventorySummaryProjection.class);
        when(projection.getProductId()).thenReturn(productId);
        when(projection.getAvailableQuantity()).thenReturn(available);
        when(projection.getReservedQuantity()).thenReturn(reserved);
        when(projection.getSoldQuantity()).thenReturn(sold);
        when(projection.getDamagedQuantity()).thenReturn(damaged);
        return projection;
    }

    // =========================================================================
    // A. getAllItems
    // =========================================================================

    @Nested
    @DisplayName("getAllItems tests")
    class GetAllItemsTests {

        @Test
        @DisplayName("getAllItems_whenItemsExist_returnsMappedPageWithPaginationAndSorting")
        void getAllItems_whenItemsExist_returnsMappedPageWithPaginationAndSorting() {
            // Given
            Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
            InventoryItem item1 = buildInventoryItem(1L, 10, "BC-001", "SN-001", InventoryStatus.AVAILABLE);
            InventoryItem item2 = buildInventoryItem(2L, 20, "BC-002", "SN-002", InventoryStatus.AVAILABLE);
            InventoryItemResponse response1 = buildInventoryItemResponse(1L, 10, "BC-001", "SN-001", InventoryStatus.AVAILABLE);
            InventoryItemResponse response2 = buildInventoryItemResponse(2L, 20, "BC-002", "SN-002", InventoryStatus.AVAILABLE);

            Page<InventoryItem> entityPage = new PageImpl<>(List.of(item1, item2), pageable, 2);
            when(inventoryItemRepository.findAll(pageable)).thenReturn(entityPage);
            when(inventoryItemMapper.toResponse(item1)).thenReturn(response1);
            when(inventoryItemMapper.toResponse(item2)).thenReturn(response2);

            // When
            Page<InventoryItemResponse> result = inventoryItemService.getAllItems(pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).containsExactly(response1, response2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getPageable()).isEqualTo(pageable);
            assertThat(result.getSort()).isEqualTo(pageable.getSort());

            verify(inventoryItemRepository).findAll(pageable);
            verify(inventoryItemMapper).toResponse(item1);
            verify(inventoryItemMapper).toResponse(item2);
        }

        @Test
        @DisplayName("getAllItems_whenNoItemsExist_returnsEmptyPage")
        void getAllItems_whenNoItemsExist_returnsEmptyPage() {
            // Given
            Pageable pageable = PageRequest.of(1, 10);
            Page<InventoryItem> emptyPage = Page.empty(pageable);
            when(inventoryItemRepository.findAll(pageable)).thenReturn(emptyPage);

            // When
            Page<InventoryItemResponse> result = inventoryItemService.getAllItems(pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();

            verify(inventoryItemRepository).findAll(pageable);
            verifyNoInteractions(inventoryItemMapper);
        }
    }

    // =========================================================================
    // B. getItemById
    // =========================================================================

    @Nested
    @DisplayName("getItemById tests")
    class GetItemByIdTests {

        @Test
        @DisplayName("getItemById_whenItemExists_returnsMappedResponse")
        void getItemById_whenItemExists_returnsMappedResponse() {
            // Given
            Long itemId = 1L;
            InventoryItem item = buildInventoryItem(itemId, 10, "BC-001", "SN-001", InventoryStatus.AVAILABLE);
            InventoryItemResponse expectedResponse = buildInventoryItemResponse(itemId, 10, "BC-001", "SN-001", InventoryStatus.AVAILABLE);

            when(inventoryItemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(inventoryItemMapper.toResponse(item)).thenReturn(expectedResponse);

            // When
            InventoryItemResponse actualResponse = inventoryItemService.getItemById(itemId);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
            verify(inventoryItemRepository).findById(itemId);
            verify(inventoryItemMapper).toResponse(item);
        }

        @Test
        @DisplayName("getItemById_whenItemNotFound_throwsResponseStatusException404")
        void getItemById_whenItemNotFound_throwsResponseStatusException404() {
            // Given
            Long missingId = 999L;
            when(inventoryItemRepository.findById(missingId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> inventoryItemService.getItemById(missingId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(rse.getReason()).contains("Inventory item not found with id: " + missingId);
                    });

            verify(inventoryItemRepository).findById(missingId);
            verifyNoInteractions(inventoryItemMapper);
        }
    }

    // =========================================================================
    // C. createItem
    // =========================================================================

    @Nested
    @DisplayName("createItem tests")
    class CreateItemTests {

        @Test
        @DisplayName("createItem_whenProductDoesNotExist_throwsProductNotFoundExceptionAndDoesNotSave")
        void createItem_whenProductDoesNotExist_throwsProductNotFoundExceptionAndDoesNotSave() {
            // Given
            CreateInventoryItemRequest request = buildCreateRequest(100, "BC-NEW", "SN-NEW");
            when(productClient.existsById(100)).thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> inventoryItemService.createItem(request))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("Product not found with ID: 100");

            verify(productClient).existsById(100);
            verifyNoInteractions(inventoryItemMapper);
            verifyNoInteractions(inventoryItemRepository);
        }

        @Test
        @DisplayName("createItem_whenBarcodeAlreadyExists_throwsInventoryItemConflictExceptionAndDoesNotSave")
        void createItem_whenBarcodeAlreadyExists_throwsInventoryItemConflictExceptionAndDoesNotSave() {
            // Given
            CreateInventoryItemRequest request = buildCreateRequest(10, "BC-DUPLICATE", "SN-NEW");
            InventoryItem item = buildInventoryItem(null, 10, "BC-DUPLICATE", "SN-NEW", InventoryStatus.AVAILABLE);

            when(productClient.existsById(10)).thenReturn(true);
            when(inventoryItemMapper.toEntity(request)).thenReturn(item);
            when(inventoryItemRepository.existsByBarcode("BC-DUPLICATE")).thenReturn(true);

            // When / Then
            assertThatThrownBy(() -> inventoryItemService.createItem(request))
                    .isInstanceOf(InventoryItemConflictException.class)
                    .hasMessageContaining("Barcode already exists: BC-DUPLICATE");

            verify(productClient).existsById(10);
            verify(inventoryItemMapper).toEntity(request);
            verify(inventoryItemRepository).existsByBarcode("BC-DUPLICATE");
            verify(inventoryItemRepository, never()).existsBySerialNumber(any());
            verify(inventoryItemRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("createItem_whenSerialNumberAlreadyExists_throwsInventoryItemConflictExceptionAndDoesNotSave")
        void createItem_whenSerialNumberAlreadyExists_throwsInventoryItemConflictExceptionAndDoesNotSave() {
            // Given
            CreateInventoryItemRequest request = buildCreateRequest(10, "BC-UNIQUE", "SN-DUPLICATE");
            InventoryItem item = buildInventoryItem(null, 10, "BC-UNIQUE", "SN-DUPLICATE", InventoryStatus.AVAILABLE);

            when(productClient.existsById(10)).thenReturn(true);
            when(inventoryItemMapper.toEntity(request)).thenReturn(item);
            when(inventoryItemRepository.existsByBarcode("BC-UNIQUE")).thenReturn(false);
            when(inventoryItemRepository.existsBySerialNumber("SN-DUPLICATE")).thenReturn(true);

            // When / Then
            assertThatThrownBy(() -> inventoryItemService.createItem(request))
                    .isInstanceOf(InventoryItemConflictException.class)
                    .hasMessageContaining("Serial number already exists: SN-DUPLICATE");

            verify(productClient).existsById(10);
            verify(inventoryItemMapper).toEntity(request);
            verify(inventoryItemRepository).existsByBarcode("BC-UNIQUE");
            verify(inventoryItemRepository).existsBySerialNumber("SN-DUPLICATE");
            verify(inventoryItemRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("createItem_whenValidRequestWithSerialNumber_savesAndReturnsMappedResponse")
        void createItem_whenValidRequestWithSerialNumber_savesAndReturnsMappedResponse() {
            // Given
            CreateInventoryItemRequest request = buildCreateRequest(10, "BC-OK", "SN-OK");
            InventoryItem itemToSave = buildInventoryItem(null, 10, "BC-OK", "SN-OK", InventoryStatus.AVAILABLE);
            InventoryItem savedItem = buildInventoryItem(1L, 10, "BC-OK", "SN-OK", InventoryStatus.AVAILABLE);
            InventoryItemResponse expectedResponse = buildInventoryItemResponse(1L, 10, "BC-OK", "SN-OK", InventoryStatus.AVAILABLE);

            when(productClient.existsById(10)).thenReturn(true);
            when(inventoryItemMapper.toEntity(request)).thenReturn(itemToSave);
            when(inventoryItemRepository.existsByBarcode("BC-OK")).thenReturn(false);
            when(inventoryItemRepository.existsBySerialNumber("SN-OK")).thenReturn(false);
            when(inventoryItemRepository.saveAndFlush(itemToSave)).thenReturn(savedItem);
            when(inventoryItemMapper.toResponse(savedItem)).thenReturn(expectedResponse);

            // When
            InventoryItemResponse actualResponse = inventoryItemService.createItem(request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);

            InOrder inOrder = inOrder(productClient, inventoryItemMapper, inventoryItemRepository);
            inOrder.verify(productClient).existsById(10);
            inOrder.verify(inventoryItemMapper).toEntity(request);
            inOrder.verify(inventoryItemRepository).existsByBarcode("BC-OK");
            inOrder.verify(inventoryItemRepository).existsBySerialNumber("SN-OK");
            inOrder.verify(inventoryItemRepository).saveAndFlush(itemToSave);
            inOrder.verify(inventoryItemMapper).toResponse(savedItem);
        }

        @Test
        @DisplayName("createItem_whenValidRequestWithoutSerialNumber_savesWithoutCheckingSerialNumber")
        void createItem_whenValidRequestWithoutSerialNumber_savesWithoutCheckingSerialNumber() {
            // Given
            CreateInventoryItemRequest request = buildCreateRequest(10, "BC-NOSN", null);
            InventoryItem itemToSave = buildInventoryItem(null, 10, "BC-NOSN", null, InventoryStatus.AVAILABLE);
            InventoryItem savedItem = buildInventoryItem(2L, 10, "BC-NOSN", null, InventoryStatus.AVAILABLE);
            InventoryItemResponse expectedResponse = buildInventoryItemResponse(2L, 10, "BC-NOSN", null, InventoryStatus.AVAILABLE);

            when(productClient.existsById(10)).thenReturn(true);
            when(inventoryItemMapper.toEntity(request)).thenReturn(itemToSave);
            when(inventoryItemRepository.existsByBarcode("BC-NOSN")).thenReturn(false);
            when(inventoryItemRepository.saveAndFlush(itemToSave)).thenReturn(savedItem);
            when(inventoryItemMapper.toResponse(savedItem)).thenReturn(expectedResponse);

            // When
            InventoryItemResponse actualResponse = inventoryItemService.createItem(request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
            verify(inventoryItemRepository, never()).existsBySerialNumber(any());
            verify(inventoryItemRepository).saveAndFlush(itemToSave);
        }

        @Test
        @DisplayName("createItem_whenDatabaseRaisesDataIntegrityViolation_translatesToInventoryItemConflictException")
        void createItem_whenDatabaseRaisesDataIntegrityViolation_translatesToInventoryItemConflictException() {
            // Given
            CreateInventoryItemRequest request = buildCreateRequest(10, "BC-RACE", "SN-RACE");
            InventoryItem itemToSave = buildInventoryItem(null, 10, "BC-RACE", "SN-RACE", InventoryStatus.AVAILABLE);

            when(productClient.existsById(10)).thenReturn(true);
            when(inventoryItemMapper.toEntity(request)).thenReturn(itemToSave);
            when(inventoryItemRepository.existsByBarcode("BC-RACE")).thenReturn(false);
            when(inventoryItemRepository.existsBySerialNumber("SN-RACE")).thenReturn(false);
            when(inventoryItemRepository.saveAndFlush(itemToSave))
                    .thenThrow(new DataIntegrityViolationException("Unique constraint violation or check constraint failure"));

            // When / Then
            assertThatThrownBy(() -> inventoryItemService.createItem(request))
                    .isInstanceOf(InventoryItemConflictException.class)
                    .hasMessage("Inventory item conflicts with existing data or violates a database constraint.");

            verify(inventoryItemRepository).saveAndFlush(itemToSave);
            verify(inventoryItemMapper, never()).toResponse(any());
        }
    }

    // =========================================================================
    // D. getInventorySummaries
    // =========================================================================

    @Nested
    @DisplayName("getInventorySummaries tests")
    class GetInventorySummariesTests {

        @Test
        @DisplayName("getInventorySummaries_whenProductIdsNull_throwsIllegalArgumentException")
        void getInventorySummaries_whenProductIdsNull_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> inventoryItemService.getInventorySummaries(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("productIds must not be empty");

            verifyNoInteractions(inventoryItemRepository);
        }

        @Test
        @DisplayName("getInventorySummaries_whenProductIdsEmpty_throwsIllegalArgumentException")
        void getInventorySummaries_whenProductIdsEmpty_throwsIllegalArgumentException() {
            List<Integer> emptyList = Collections.emptyList();

            assertThatThrownBy(() -> inventoryItemService.getInventorySummaries(emptyList))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("productIds must not be empty");

            verifyNoInteractions(inventoryItemRepository);
        }

        @Test
        @DisplayName("getInventorySummaries_whenProductIdsContainsNull_throwsIllegalArgumentException")
        void getInventorySummaries_whenProductIdsContainsNull_throwsIllegalArgumentException() {
            List<Integer> listWithNull = Arrays.asList(1, null, 2);

            assertThatThrownBy(() -> inventoryItemService.getInventorySummaries(listWithNull))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("productIds must contain positive IDs");

            verifyNoInteractions(inventoryItemRepository);
        }

        @Test
        @DisplayName("getInventorySummaries_whenProductIdsContainsZero_throwsIllegalArgumentException")
        void getInventorySummaries_whenProductIdsContainsZero_throwsIllegalArgumentException() {
            List<Integer> listWithZero = List.of(1, 0, 3);

            assertThatThrownBy(() -> inventoryItemService.getInventorySummaries(listWithZero))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("productIds must contain positive IDs");

            verifyNoInteractions(inventoryItemRepository);
        }

        @Test
        @DisplayName("getInventorySummaries_whenProductIdsContainsNegative_throwsIllegalArgumentException")
        void getInventorySummaries_whenProductIdsContainsNegative_throwsIllegalArgumentException() {
            List<Integer> listWithNegative = List.of(1, -5, 3);

            assertThatThrownBy(() -> inventoryItemService.getInventorySummaries(listWithNegative))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("productIds must contain positive IDs");

            verifyNoInteractions(inventoryItemRepository);
        }

        @Test
        @DisplayName("getInventorySummaries_whenDuplicateProductIdsProvided_deduplicatesAndPreservesOrder")
        void getInventorySummaries_whenDuplicateProductIdsProvided_deduplicatesAndPreservesOrder() {
            // Given input with duplicates in non-sorted order: 3, 1, 3, 2, 1 -> unique: 3, 1, 2
            List<Integer> inputIds = List.of(3, 1, 3, 2, 1);
            List<Integer> expectedUniqueIds = List.of(3, 1, 2);

            InventorySummaryProjection p3 = buildMockProjection(3, 4L, 1L, 0L, 0L);
            InventorySummaryProjection p1 = buildMockProjection(1, 2L, 0L, 1L, 0L);
            InventorySummaryProjection p2 = buildMockProjection(2, 5L, 0L, 0L, 1L);

            when(inventoryItemRepository.summarizeByProductIds(
                    expectedUniqueIds,
                    InventoryStatus.AVAILABLE,
                    InventoryStatus.RESERVED,
                    InventoryStatus.SOLD,
                    InventoryStatus.DAMAGED
            )).thenReturn(List.of(p1, p2, p3)); // DB may return in arbitrary order

            // When
            List<InventorySummaryResponse> summaries = inventoryItemService.getInventorySummaries(inputIds);

            // Then
            assertThat(summaries).hasSize(3);
            assertThat(summaries.get(0).productId()).isEqualTo(3);
            assertThat(summaries.get(0).availableQuantity()).isEqualTo(4L);
            assertThat(summaries.get(0).reservedQuantity()).isEqualTo(1L);

            assertThat(summaries.get(1).productId()).isEqualTo(1);
            assertThat(summaries.get(1).availableQuantity()).isEqualTo(2L);
            assertThat(summaries.get(1).soldQuantity()).isEqualTo(1L);

            assertThat(summaries.get(2).productId()).isEqualTo(2);
            assertThat(summaries.get(2).availableQuantity()).isEqualTo(5L);
            assertThat(summaries.get(2).damagedQuantity()).isEqualTo(1L);

            verify(inventoryItemRepository).summarizeByProductIds(
                    expectedUniqueIds,
                    InventoryStatus.AVAILABLE,
                    InventoryStatus.RESERVED,
                    InventoryStatus.SOLD,
                    InventoryStatus.DAMAGED
            );
        }

        @Test
        @DisplayName("getInventorySummaries_whenProjectionsFound_mapsToResponseWithCorrectQuantities")
        void getInventorySummaries_whenProjectionsFound_mapsToResponseWithCorrectQuantities() {
            // Given
            List<Integer> productIds = List.of(10);
            InventorySummaryProjection projection = buildMockProjection(10, 8L, 3L, 5L, 2L);

            when(inventoryItemRepository.summarizeByProductIds(
                    productIds,
                    InventoryStatus.AVAILABLE,
                    InventoryStatus.RESERVED,
                    InventoryStatus.SOLD,
                    InventoryStatus.DAMAGED
            )).thenReturn(List.of(projection));

            // When
            List<InventorySummaryResponse> results = inventoryItemService.getInventorySummaries(productIds);

            // Then
            assertThat(results).hasSize(1);
            InventorySummaryResponse summary = results.getFirst();
            assertThat(summary.productId()).isEqualTo(10);
            assertThat(summary.availableQuantity()).isEqualTo(8L);
            assertThat(summary.reservedQuantity()).isEqualTo(3L);
            assertThat(summary.soldQuantity()).isEqualTo(5L);
            assertThat(summary.damagedQuantity()).isEqualTo(2L);
        }

        @Test
        @DisplayName("getInventorySummaries_whenProductHasNoInventoryRecords_returnsZeroSummary")
        void getInventorySummaries_whenProductHasNoInventoryRecords_returnsZeroSummary() {
            // Given
            List<Integer> productIds = List.of(999);
            when(inventoryItemRepository.summarizeByProductIds(
                    productIds,
                    InventoryStatus.AVAILABLE,
                    InventoryStatus.RESERVED,
                    InventoryStatus.SOLD,
                    InventoryStatus.DAMAGED
            )).thenReturn(Collections.emptyList());

            // When
            List<InventorySummaryResponse> results = inventoryItemService.getInventorySummaries(productIds);

            // Then
            assertThat(results).hasSize(1);
            InventorySummaryResponse summary = results.getFirst();
            assertThat(summary.productId()).isEqualTo(999);
            assertThat(summary.availableQuantity()).isZero();
            assertThat(summary.reservedQuantity()).isZero();
            assertThat(summary.soldQuantity()).isZero();
            assertThat(summary.damagedQuantity()).isZero();
        }

        @Test
        @DisplayName("getInventorySummaries_whenProjectionAggregateValuesAreNull_convertsNullsToZero")
        void getInventorySummaries_whenProjectionAggregateValuesAreNull_convertsNullsToZero() {
            // Given projection with null aggregate values
            List<Integer> productIds = List.of(42);
            InventorySummaryProjection projection = buildMockProjection(42, null, null, null, null);

            when(inventoryItemRepository.summarizeByProductIds(
                    productIds,
                    InventoryStatus.AVAILABLE,
                    InventoryStatus.RESERVED,
                    InventoryStatus.SOLD,
                    InventoryStatus.DAMAGED
            )).thenReturn(List.of(projection));

            // When
            List<InventorySummaryResponse> results = inventoryItemService.getInventorySummaries(productIds);

            // Then
            assertThat(results).hasSize(1);
            InventorySummaryResponse summary = results.getFirst();
            assertThat(summary.productId()).isEqualTo(42);
            assertThat(summary.availableQuantity()).isZero();
            assertThat(summary.reservedQuantity()).isZero();
            assertThat(summary.soldQuantity()).isZero();
            assertThat(summary.damagedQuantity()).isZero();
        }
    }
}
