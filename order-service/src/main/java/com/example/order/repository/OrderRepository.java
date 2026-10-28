package com.example.order.repository;

import com.example.order.entity.Order;
import com.example.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderReference(String orderReference);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM Order o WHERE o.orderReference = :orderReference")
    Optional<Order> findWithItemsByOrderReference(@Param("orderReference") String orderReference);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.idempotencyKey = :idempotencyKey")
    Optional<Order> findWithItemsByUserIdAndIdempotencyKey(@Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    Page<Order> findAll(Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM Order o WHERE o.status IN :statuses ORDER BY o.updatedAt ASC")
    List<Order> findByStatusInWithItems(@Param("statuses") Collection<OrderStatus> statuses, Pageable pageable);
}
