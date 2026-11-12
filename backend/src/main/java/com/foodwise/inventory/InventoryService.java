package com.foodwise.inventory;

import com.foodwise.catalog.CatalogItemDto;
import com.foodwise.catalog.CatalogService;
import com.foodwise.common.ApiException;
import com.foodwise.common.AppClock;
import com.foodwise.common.Units;
import com.foodwise.common.enums.BatchStatus;
import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.Unit;
import com.foodwise.common.enums.WasteReason;
import com.foodwise.security.KitchenAuthGuard;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Kitchen inventory (Roadmap M2/M3). Every public method authorizes kitchen membership first and every quantity
 * mutation locks the batch row ({@code SELECT ... FOR UPDATE}) inside one transaction.
 */
@Service
public class InventoryService {

    private final InventoryBatchRepository batchRepo;
    private final InventoryEventRepository eventRepo;
    private final InventoryOperations ops;
    private final BatchMapper mapper;
    private final CatalogService catalog;
    private final KitchenAuthGuard guard;
    private final AppClock clock;

    public InventoryService(InventoryBatchRepository batchRepo, InventoryEventRepository eventRepo, InventoryOperations ops,
                            BatchMapper mapper, CatalogService catalog, KitchenAuthGuard guard, AppClock clock) {
        this.batchRepo = batchRepo;
        this.eventRepo = eventRepo;
        this.ops = ops;
        this.mapper = mapper;
        this.catalog = catalog;
        this.guard = guard;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ requests

    public record CreateBatchRequest(
            UUID catalogItemId,
            @Size(max = 100) String name,
            UUID categoryId,
            StorageLocation storageLocation,
            @NotNull @Positive @DecimalMax("99999999") BigDecimal quantity,
            @NotNull Unit unit,
            LocalDate purchaseDate,
            LocalDate expiryDate,
            @DecimalMin("0") @DecimalMax("99999999") BigDecimal costInr,
            @Size(max = 100) String storeName) {
    }

    /** Either {@code delta} (amount used, > 0) or {@code quantity} (absolute correction) must be given. */
    public record AdjustQuantityRequest(@Positive BigDecimal delta, @DecimalMin("0") BigDecimal quantity, @Size(max = 300) String reason) {
    }

    public record MoveLocationRequest(@NotNull StorageLocation location, LocalDate expiryDate) {
    }

    public record UpdateBatchRequest(@Size(min = 1, max = 100) String name, UUID categoryId, LocalDate expiryDate,
                                     @DecimalMin("0") @DecimalMax("99999999") BigDecimal costInr,
                                     @Size(max = 500) String notes) {
    }

    public enum BulkAction { CONSUME, DISCARD, MOVE }

    public record BulkRequest(@NotEmpty @Size(max = 100) List<UUID> batchIds, @NotNull BulkAction action,
                              StorageLocation location, WasteReason reason) {
    }

    public record EventDto(UUID id, String eventType, BigDecimal quantityDelta, BigDecimal quantityAfter, String reason,
                           UUID recipeId, Instant createdAt) {
    }

    public record LowStockItem(UUID catalogItemId, String name, UUID categoryId, Unit unit, BigDecimal remaining,
                               BigDecimal suggestedQuantity) {
    }

    public enum SortBy { EXPIRY_ASC, EXPIRY_DESC, ADDED_DESC, NAME_ASC }

    public record ListFilter(StorageLocation location, UUID categoryId, BatchStatus status, String q, SortBy sort,
                             Boolean leftovers) {
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<BatchDto> list(UUID kitchenId, UUID userId, ListFilter filter) {
        guard.requireMembership(kitchenId, userId);
        BatchStatus status = filter.status() == null ? BatchStatus.ACTIVE : filter.status();
        LocalDate today = clock.today();
        String q = filter.q() == null ? null : filter.q().trim().toLowerCase(Locale.ROOT);
        Comparator<BatchDto> order = switch (filter.sort() == null ? SortBy.EXPIRY_ASC : filter.sort()) {
            case EXPIRY_ASC -> Comparator.comparing(BatchDto::expiryDate).thenComparing(BatchDto::name, String.CASE_INSENSITIVE_ORDER);
            case EXPIRY_DESC -> Comparator.comparing(BatchDto::expiryDate).reversed().thenComparing(BatchDto::name, String.CASE_INSENSITIVE_ORDER);
            case ADDED_DESC -> Comparator.comparing(BatchDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case NAME_ASC -> Comparator.comparing(BatchDto::name, String.CASE_INSENSITIVE_ORDER);
        };
        return batchRepo.findByKitchenIdAndStatus(kitchenId, status).stream()
                .filter(b -> filter.location() == null || b.getStorageLocation() == filter.location())
                .filter(b -> filter.categoryId() == null || filter.categoryId().equals(b.getCategoryId()))
                .filter(b -> filter.leftovers() == null || b.isLeftover() == filter.leftovers())
                .map(b -> mapper.toDto(b, today))
                .filter(d -> q == null || q.isEmpty() || matches(d, q))
                .sorted(order)
                .limit(1000)
                .toList();
    }

    private static boolean matches(BatchDto d, String q) {
        return d.name().toLowerCase(Locale.ROOT).contains(q)
                || (d.nameHi() != null && d.nameHi().toLowerCase(Locale.ROOT).contains(q))
                || (d.categoryName() != null && d.categoryName().toLowerCase(Locale.ROOT).contains(q));
    }

    @Transactional(readOnly = true)
    public BatchDto get(UUID kitchenId, UUID userId, UUID batchId) {
        guard.requireMembership(kitchenId, userId);
        return mapper.toDto(find(kitchenId, batchId));
    }

    @Transactional(readOnly = true)
    public List<EventDto> events(UUID kitchenId, UUID userId, UUID batchId) {
        guard.requireMembership(kitchenId, userId);
        find(kitchenId, batchId);
        return eventRepo.findByBatchIdOrderByCreatedAtDesc(batchId).stream()
                .map(e -> new EventDto(e.getId(), e.getEventType().dbValue(), e.getQuantityDelta(), e.getQuantityAfter(),
                        e.getReason(), e.getRecipeId(), e.getCreatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public BatchDto create(UUID kitchenId, UUID userId, CreateBatchRequest req) {
        guard.requireMembership(kitchenId, userId);
        InventoryBatch b = ops.createBatch(new InventoryOperations.NewBatch(kitchenId, req.catalogItemId(), req.name(),
                req.categoryId(), req.storageLocation(), req.quantity(), req.unit(), req.purchaseDate(), req.expiryDate(),
                req.costInr(), false, null, null, null), userId, "");
        if (req.costInr() != null) {
            ops.recordPurchase(kitchenId, userId, b.getPurchaseDate(), req.storeName(), null, null,
                    List.of(new InventoryOperations.PurchaseLine(b, req.costInr())));
        }
        return mapper.toDto(b);
    }

    /** PATCH quantity: {@code delta} = amount used (FR-INV-004) or {@code quantity} = absolute correction. */
    @Transactional
    public BatchDto adjustQuantity(UUID kitchenId, UUID userId, UUID batchId, AdjustQuantityRequest req) {
        guard.requireMembership(kitchenId, userId);
        if ((req.delta() == null) == (req.quantity() == null)) {
            throw ApiException.validation("delta", "Provide either delta (amount used) or quantity (new amount).");
        }
        InventoryBatch locked = lock(kitchenId, batchId);
        InventoryBatch updated = req.delta() != null
                ? ops.deduct(locked, req.delta(), userId, null, req.reason())
                : ops.setQuantity(locked, req.quantity(), userId, req.reason());
        return mapper.toDto(updated);
    }

    @Transactional
    public BatchDto consume(UUID kitchenId, UUID userId, UUID batchId) {
        guard.requireMembership(kitchenId, userId);
        return mapper.toDto(ops.consumeAll(lock(kitchenId, batchId), userId, "Marked as consumed"));
    }

    @Transactional
    public BatchDto moveLocation(UUID kitchenId, UUID userId, UUID batchId, MoveLocationRequest req) {
        guard.requireMembership(kitchenId, userId);
        return mapper.toDto(ops.moveLocation(lock(kitchenId, batchId), req.location(), req.expiryDate(), userId));
    }

    @Transactional
    public BatchDto update(UUID kitchenId, UUID userId, UUID batchId, UpdateBatchRequest req) {
        guard.requireMembership(kitchenId, userId);
        InventoryBatch b = lock(kitchenId, batchId);
        if (req.name() != null) {
            b.setCustomItemName(req.name().trim());
        }
        if (req.categoryId() != null) {
            if (catalog.category(req.categoryId()).isEmpty()) {
                throw ApiException.validation("categoryId", "Unknown category.");
            }
            b.setCategoryId(req.categoryId());
        }
        if (req.expiryDate() != null) {
            if (req.expiryDate().isBefore(b.getPurchaseDate())) {
                throw ApiException.validation("expiryDate", "Expiry date cannot be before the purchase date.");
            }
            b.setExpiryDate(req.expiryDate());
            b.setExpiryEstimated(false);
        }
        if (req.costInr() != null) {
            b.setCostInr(Units.money(req.costInr()));
        }
        if (req.notes() != null) {
            b.setLeftoverNotes(req.notes().isBlank() ? null : req.notes().trim());
        }
        return mapper.toDto(batchRepo.save(b));
    }

    public record DiscardResult(BatchDto batch, BigDecimal estimatedLossInr, WasteReason reason) {
    }

    @Transactional
    public DiscardResult discard(UUID kitchenId, UUID userId, UUID batchId, WasteReason reason, BigDecimal quantity) {
        guard.requireMembership(kitchenId, userId);
        InventoryBatch locked = lock(kitchenId, batchId);
        FoodWasteLog log = ops.discard(locked, quantity, reason, userId);
        return new DiscardResult(mapper.toDto(locked), log.getEstimatedLossInr(), log.getWasteReason());
    }

    @Transactional
    public List<BatchDto> bulk(UUID kitchenId, UUID userId, BulkRequest req) {
        guard.requireMembership(kitchenId, userId);
        if (req.action() == BulkAction.MOVE && req.location() == null) {
            throw ApiException.validation("location", "Location is required to move items.");
        }
        List<UUID> ids = req.batchIds().stream().distinct().sorted().toList(); // stable lock order
        List<BatchDto> out = new ArrayList<>();
        for (UUID id : ids) {
            InventoryBatch b = lock(kitchenId, id);
            switch (req.action()) {
                case CONSUME -> ops.consumeAll(b, userId, "Bulk: marked as consumed");
                case DISCARD -> ops.discard(b, null, req.reason(), userId);
                case MOVE -> ops.moveLocation(b, req.location(), null, userId);
            }
            out.add(mapper.toDto(b));
        }
        return out;
    }

    /**
     * Items the kitchen regularly buys that are now out or low (remaining <= 25% of the last pack bought),
     * used for shopping auto-replenish (FR-SHP-001).
     */
    @Transactional(readOnly = true)
    public List<LowStockItem> lowStock(UUID kitchenId, UUID userId) {
        guard.requireMembership(kitchenId, userId);
        return lowStockInternal(kitchenId);
    }

    /** Same as {@link #lowStock} without the membership check; callers must have authorized already. */
    public List<LowStockItem> lowStockInternal(UUID kitchenId) {
        LocalDate since = clock.today().minusDays(90);
        Map<UUID, List<InventoryBatch>> byItem = new LinkedHashMap<>();
        for (InventoryBatch b : batchRepo.findRecentCatalogBatches(kitchenId, since)) {
            byItem.computeIfAbsent(b.getCatalogItemId(), k -> new ArrayList<>()).add(b);
        }
        List<LowStockItem> out = new ArrayList<>();
        for (Map.Entry<UUID, List<InventoryBatch>> e : byItem.entrySet()) {
            InventoryBatch latest = e.getValue().getFirst();
            BigDecimal remaining = BigDecimal.ZERO;
            for (InventoryBatch b : e.getValue()) {
                if (b.getStatus() == BatchStatus.ACTIVE) {
                    remaining = remaining.add(Units.convert(b.getCurrentQuantity(), b.getUnit(), latest.getUnit()).orElse(BigDecimal.ZERO));
                }
            }
            BigDecimal threshold = latest.getInitialQuantity().multiply(new BigDecimal("0.25"));
            if (remaining.compareTo(threshold) <= 0) {
                Optional<CatalogItemDto> item = catalog.item(e.getKey());
                out.add(new LowStockItem(e.getKey(), item.map(CatalogItemDto::nameEn).orElse(latest.getCustomItemName()),
                        latest.getCategoryId(), latest.getUnit(), Units.qty(remaining), latest.getInitialQuantity()));
            }
        }
        out.sort(Comparator.comparing(LowStockItem::name));
        return out;
    }

    // ------------------------------------------------------------------ export

    private static final Set<Character> FORMULA_PREFIXES = Set.of('=', '+', '-', '@', '\t', '\r');

    @Transactional(readOnly = true)
    public String exportCsv(UUID kitchenId, UUID userId) {
        List<BatchDto> rows = list(kitchenId, userId, new ListFilter(null, null, BatchStatus.ACTIVE, null, SortBy.EXPIRY_ASC, null));
        StringBuilder sb = new StringBuilder("name,category,location,quantity,unit,purchase_date,expiry_date,expiry_status,cost_inr,leftover\n");
        for (BatchDto r : rows) {
            sb.append(csv(r.name())).append(',').append(csv(r.categoryName())).append(',')
                    .append(r.storageLocation().dbValue()).append(',').append(r.currentQuantity().toPlainString()).append(',')
                    .append(r.unit().dbValue()).append(',').append(r.purchaseDate()).append(',').append(r.expiryDate()).append(',')
                    .append(r.expiryStatus().value()).append(',')
                    .append(r.costInr() == null ? "" : r.costInr().toPlainString()).append(',')
                    .append(r.leftover()).append('\n');
        }
        return sb.toString();
    }

    /** RFC 4180 quoting plus CSV-injection guard for spreadsheet apps. */
    static String csv(String v) {
        if (v == null) {
            return "";
        }
        String s = !v.isEmpty() && FORMULA_PREFIXES.contains(v.charAt(0)) ? "'" + v : v;
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ------------------------------------------------------------------ helpers

    private InventoryBatch find(UUID kitchenId, UUID batchId) {
        return batchRepo.findByIdAndKitchenId(batchId, kitchenId).orElseThrow(() -> ApiException.notFound("Item not found."));
    }

    private InventoryBatch lock(UUID kitchenId, UUID batchId) {
        return batchRepo.findByIdAndKitchenIdForUpdate(batchId, kitchenId).orElseThrow(() -> ApiException.notFound("Item not found."));
    }
}
