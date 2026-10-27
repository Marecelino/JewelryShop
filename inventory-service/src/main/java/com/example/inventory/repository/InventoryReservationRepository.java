package com.example.inventory.repository;

import com.example.inventory.entity.InventoryReservation;
import com.example.inventory.enums.InventoryReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryReservationRepository
        extends JpaRepository<InventoryReservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM InventoryReservation r WHERE r.id = :id")
    Optional<InventoryReservation> findByIdForUpdate(@Param("id") Long id);

    Optional<InventoryReservation> findByOrderReferenceAndOrderItemReference(
            String orderReference,
            String orderItemReference
    );

    List<InventoryReservation> findTop50ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            InventoryReservationStatus status,
            Instant now
    );
}
