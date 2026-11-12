package com.foodwise.expiry;

import com.foodwise.common.AppClock;
import com.foodwise.common.enums.BatchStatus;
import com.foodwise.inventory.BatchDto;
import com.foodwise.inventory.BatchMapper;
import com.foodwise.inventory.InventoryBatch;
import com.foodwise.inventory.InventoryBatchRepository;
import com.foodwise.security.KitchenAuthGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Expiry triage views (Roadmap 3.B2 / UI Screen 8). Purely derived from expiry_date; nothing is persisted. */
@Service
public class ExpiryService {

    private final InventoryBatchRepository batchRepo;
    private final BatchMapper mapper;
    private final KitchenAuthGuard guard;
    private final AppClock clock;

    public ExpiryService(InventoryBatchRepository batchRepo, BatchMapper mapper, KitchenAuthGuard guard, AppClock clock) {
        this.batchRepo = batchRepo;
        this.mapper = mapper;
        this.guard = guard;
        this.clock = clock;
    }

    public record Timeline(List<BatchDto> expiringToday, List<BatchDto> nextThreeDays, List<BatchDto> thisWeek,
                           List<BatchDto> later, List<BatchDto> expired, BigDecimal valueAtRiskInr) {
    }

    @Transactional(readOnly = true)
    public Timeline timeline(UUID kitchenId, UUID userId) {
        guard.requireMembership(kitchenId, userId);
        LocalDate today = clock.today();
        List<BatchDto> today0 = new ArrayList<>(), soon = new ArrayList<>(), week = new ArrayList<>(),
                later = new ArrayList<>(), expired = new ArrayList<>();
        BigDecimal atRisk = BigDecimal.ZERO;
        List<InventoryBatch> batches = batchRepo.findByKitchenIdAndStatus(kitchenId, BatchStatus.ACTIVE);
        batches.sort(Comparator.comparing(InventoryBatch::getExpiryDate).thenComparing(InventoryBatch::getCustomItemName));
        for (InventoryBatch b : batches) {
            BatchDto d = mapper.toDto(b, today);
            switch (d.expiryStatus()) {
                case EXPIRED -> expired.add(d);
                case EXPIRING_TODAY -> today0.add(d);
                case USE_SOON -> soon.add(d);
                case FRESH -> (d.daysUntilExpiry() <= 7 ? week : later).add(d);
            }
            if (d.expiryStatus().isUrgent()) {
                atRisk = atRisk.add(remainingValue(b));
            }
        }
        return new Timeline(today0, soon, week, later, expired, atRisk.setScale(2, RoundingMode.HALF_UP));
    }

    /** USE_SOON and EXPIRING_TODAY batches, most urgent first. */
    @Transactional(readOnly = true)
    public List<BatchDto> urgent(UUID kitchenId, UUID userId) {
        guard.requireMembership(kitchenId, userId);
        LocalDate today = clock.today();
        return batchRepo.findActiveExpiringOnOrBefore(kitchenId, today.plusDays(ExpiryClassifier.USE_SOON_MAX_DAYS)).stream()
                .map(b -> mapper.toDto(b, today))
                .filter(d -> d.expiryStatus().isUrgent())
                .toList();
    }

    /** Remaining rupee value of a batch: cost x current / initial. */
    public static BigDecimal remainingValue(InventoryBatch b) {
        if (b.getCostInr() == null || b.getInitialQuantity().signum() == 0) {
            return BigDecimal.ZERO;
        }
        return b.getCostInr().multiply(b.getCurrentQuantity()).divide(b.getInitialQuantity(), 2, RoundingMode.HALF_UP);
    }
}
