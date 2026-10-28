package com.example.order.repository;

import com.example.order.entity.OrderItem;
import com.example.order.enums.OrderItemStatus;
import com.example.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder_OrderId(Long orderId);

    List<OrderItem> findByOrder_OrderIdAndStatus(Long orderId, OrderItemStatus status);

    List<OrderItem> findByOrder_OrderReference(String orderReference);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o " +
           "WHERE oi.status = :status " +
           "AND o.status IN :orderStatuses " +
           "AND (oi.nextRetryAt IS NULL OR oi.nextRetryAt <= :now) " +
           "ORDER BY oi.updatedAt ASC")
    List<OrderItem> findDueForRetry(
            @Param("status") OrderItemStatus status,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o " +
           "WHERE oi.status IN :statuses " +
           "AND o.status IN :orderStatuses " +
           "AND (oi.nextRetryAt IS NULL OR oi.nextRetryAt <= :now) " +
           "ORDER BY oi.updatedAt ASC")
    List<OrderItem> findItemsNeedingCompensation(
            @Param("statuses") Collection<OrderItemStatus> statuses,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE OrderItem oi SET oi.status = :targetStatus, oi.lastErrorCode = :errorCode, oi.updatedAt = :now " +
           "WHERE oi.order.orderId = :orderId AND oi.status = :sourceStatus")
    int updateStatusForRemainingItems(
            @Param("orderId") Long orderId,
            @Param("sourceStatus") OrderItemStatus sourceStatus,
            @Param("targetStatus") OrderItemStatus targetStatus,
            @Param("errorCode") String errorCode,
            @Param("now") Instant now
    );
}
