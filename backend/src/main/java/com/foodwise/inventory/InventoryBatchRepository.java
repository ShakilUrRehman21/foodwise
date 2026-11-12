package com.foodwise.inventory;

import com.foodwise.common.enums.BatchStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, UUID> {

    Optional<InventoryBatch> findByIdAndKitchenId(UUID id, UUID kitchenId);

    /** {@code SELECT ... FOR UPDATE}: the single concurrency strategy for quantity mutations (SYSTEM_DESIGN 7.2). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select b from InventoryBatch b where b.id = :id and b.kitchenId = :kitchenId")
    Optional<InventoryBatch> findByIdAndKitchenIdForUpdate(UUID id, UUID kitchenId);

    /** Locks all active batches of the given catalog items in a stable order (by id) to avoid deadlocks. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
            select b from InventoryBatch b
            where b.kitchenId = :kitchenId and b.status = :status
              and b.leftover = false and b.catalogItemId in :catalogItemIds
            order by b.id
            """)
    List<InventoryBatch> lockByCatalogItems(UUID kitchenId, Collection<UUID> catalogItemIds, BatchStatus status);

    default List<InventoryBatch> lockActiveByCatalogItems(UUID kitchenId, Collection<UUID> catalogItemIds) {
        return lockByCatalogItems(kitchenId, catalogItemIds, BatchStatus.ACTIVE);
    }

    List<InventoryBatch> findByKitchenIdAndStatus(UUID kitchenId, BatchStatus status);

    @Query("""
            select b from InventoryBatch b
            where b.kitchenId = :kitchenId and b.status = :status and b.expiryDate <= :date
            order by b.expiryDate, b.customItemName
            """)
    List<InventoryBatch> findExpiringOnOrBefore(UUID kitchenId, LocalDate date, BatchStatus status);

    default List<InventoryBatch> findActiveExpiringOnOrBefore(UUID kitchenId, LocalDate date) {
        return findExpiringOnOrBefore(kitchenId, date, BatchStatus.ACTIVE);
    }

    @Query("""
            select b from InventoryBatch b
            where b.status = :status and b.expiryDate = :date
            order by b.kitchenId, b.customItemName
            """)
    List<InventoryBatch> findAllExpiringOn(LocalDate date, BatchStatus status);

    default List<InventoryBatch> findAllActiveExpiringOn(LocalDate date) {
        return findAllExpiringOn(date, BatchStatus.ACTIVE);
    }

    @Query("""
            select b from InventoryBatch b
            where b.kitchenId = :kitchenId and b.leftover = true and b.status = :status
            order by b.expiryDate, b.cookedAt desc
            """)
    List<InventoryBatch> findLeftovers(UUID kitchenId, BatchStatus status);

    default List<InventoryBatch> findActiveLeftovers(UUID kitchenId) {
        return findLeftovers(kitchenId, BatchStatus.ACTIVE);
    }

    @Query("""
            select b from InventoryBatch b
            where b.kitchenId = :kitchenId and b.leftover = false and b.catalogItemId is not null and b.purchaseDate >= :since
            order by b.purchaseDate desc, b.createdAt desc
            """)
    List<InventoryBatch> findRecentCatalogBatches(UUID kitchenId, LocalDate since);
}
