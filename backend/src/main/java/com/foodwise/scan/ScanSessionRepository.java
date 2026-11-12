package com.foodwise.scan;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanSessionRepository extends JpaRepository<ScanSession, UUID> {

    Optional<ScanSession> findByIdAndKitchenId(UUID id, UUID kitchenId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScanSession s where s.id = :id and s.kitchenId = :kitchenId")
    Optional<ScanSession> lockByIdAndKitchenId(UUID id, UUID kitchenId);

    List<ScanSession> findByKitchenIdOrderByCreatedAtDesc(UUID kitchenId, Pageable pageable);
}
