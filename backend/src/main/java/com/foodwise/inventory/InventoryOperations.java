package com.foodwise.inventory;

import com.foodwise.catalog.CatalogItemDto;
import com.foodwise.catalog.CatalogService;
import com.foodwise.catalog.CategoryDto;
import com.foodwise.common.ApiException;
import com.foodwise.common.AppClock;
import com.foodwise.common.Units;
import com.foodwise.common.enums.BatchStatus;
import com.foodwise.common.enums.InventoryEventType;
import com.foodwise.common.enums.LeftoverType;
import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.Unit;
import com.foodwise.common.enums.WasteReason;
import com.foodwise.expiry.ExpiryClassifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Low-level inventory ledger operations shared by inventory, scan, recipe, leftover and shopping services.
 * Every method REQUIRES an existing transaction (MANDATORY) and expects already-locked batches where it mutates
 * quantities, so a batch change and its {@code inventory_events} row always commit atomically (DATABASE_SCHEMA 4).
 * Kitchen authorization is the caller's responsibility (service layer).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class InventoryOperations {

    static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999.99");

    private final InventoryBatchRepository batchRepo;
    private final InventoryEventRepository eventRepo;
    private final FoodWasteLogRepository wasteRepo;
    private final GroceryPurchaseRepository purchaseRepo;
    private final PurchaseItemRepository purchaseItemRepo;
    private final CatalogService catalog;
    private final AppClock clock;

    public InventoryOperations(InventoryBatchRepository batchRepo, InventoryEventRepository eventRepo,
                               FoodWasteLogRepository wasteRepo, GroceryPurchaseRepository purchaseRepo,
                               PurchaseItemRepository purchaseItemRepo, CatalogService catalog, AppClock clock) {
        this.batchRepo = batchRepo;
        this.eventRepo = eventRepo;
        this.wasteRepo = wasteRepo;
        this.purchaseRepo = purchaseRepo;
        this.purchaseItemRepo = purchaseItemRepo;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** Everything needed to create one batch. {@code expiryDate} may be null only if validated catalog guidance exists. */
    public record NewBatch(UUID kitchenId, UUID catalogItemId, String name, UUID categoryId, StorageLocation location,
                           BigDecimal quantity, Unit unit, LocalDate purchaseDate, LocalDate expiryDate, BigDecimal costInr,
                           boolean leftover, LeftoverType leftoverType, Instant cookedAt, String leftoverNotes) {
    }

    public InventoryBatch createBatch(NewBatch spec, UUID userId, String fieldPrefix) {
        String f = fieldPrefix == null ? "" : fieldPrefix;
        CatalogItemDto item = spec.catalogItemId() == null ? null : catalog.requireItem(spec.catalogItemId());

        String name = spec.name() != null && !spec.name().isBlank() ? spec.name().trim() : item == null ? null : item.nameEn();
        if (name == null || name.isBlank()) {
            throw ApiException.validation(f + "name", "Item name is required.");
        }
        if (name.length() > 100) {
            throw ApiException.validation(f + "name", "Item name must be at most 100 characters.");
        }
        if (!Units.isInventoryUnit(spec.unit())) {
            throw ApiException.validation(f + "unit", "Unit must be one of g, kg, ml, L, pcs, pkt, bunch.");
        }
        BigDecimal qty = spec.quantity() == null ? null : Units.qty(spec.quantity());
        if (qty == null || qty.signum() <= 0 || qty.compareTo(MAX_QUANTITY) > 0) {
            throw ApiException.validation(f + "quantity", "Quantity must be greater than 0.");
        }
        if (spec.costInr() != null && (spec.costInr().signum() < 0 || spec.costInr().compareTo(MAX_QUANTITY) > 0)) {
            throw ApiException.validation(f + "costInr", "Price must be 0 or more.");
        }
        LocalDate today = clock.today();
        LocalDate purchaseDate = spec.purchaseDate() == null ? today : spec.purchaseDate();
        if (purchaseDate.isAfter(today)) {
            throw ApiException.validation(f + "purchaseDate", "Purchase date cannot be in the future.");
        }
        StorageLocation location = spec.location() != null ? spec.location()
                : item != null ? item.defaultLocation() : StorageLocation.FRIDGE;

        LocalDate expiry = spec.expiryDate();
        boolean estimated = false;
        if (expiry == null && item != null) {
            expiry = catalog.estimateExpiry(item.id(), location, purchaseDate).orElse(null);
            estimated = expiry != null;
        }
        if (expiry == null) {
            // Food-safety policy: never invent an expiry date when no validated guidance exists (FR-EXP-001).
            throw ApiException.validation(f + "expiryDate",
                    "Please set an expiry date for " + name + ". No validated shelf-life guidance exists for it yet.");
        }
        if (expiry.isBefore(purchaseDate)) {
            throw ApiException.validation(f + "expiryDate", "Expiry date cannot be before the purchase date.");
        }

        UUID categoryId = spec.categoryId();
        if (categoryId != null && catalog.category(categoryId).isEmpty()) {
            throw ApiException.validation(f + "categoryId", "Unknown category.");
        }
        if (categoryId == null && item != null) {
            categoryId = item.categoryId();
        }
        if (categoryId == null && spec.leftover()) {
            categoryId = catalog.categoryBySlug("cooked-food").map(CategoryDto::id).orElse(null);
        }

        InventoryBatch b = new InventoryBatch();
        b.setKitchenId(spec.kitchenId());
        b.setCatalogItemId(item == null ? null : item.id());
        b.setCustomItemName(name);
        b.setCategoryId(categoryId);
        b.setStorageLocation(location);
        b.setInitialQuantity(qty);
        b.setCurrentQuantity(qty);
        b.setUnit(spec.unit());
        b.setPurchaseDate(purchaseDate);
        b.setExpiryDate(expiry);
        b.setExpiryEstimated(estimated);
        b.setCostInr(Units.money(spec.costInr()));
        b.setLeftover(spec.leftover());
        b.setLeftoverType(spec.leftover() ? (spec.leftoverType() == null ? LeftoverType.OTHER : spec.leftoverType()) : null);
        b.setCookedAt(spec.leftover() ? (spec.cookedAt() == null ? Instant.now() : spec.cookedAt()) : null);
        b.setLeftoverNotes(trimTo(spec.leftoverNotes(), 500));
        b.setStatus(BatchStatus.ACTIVE);
        b = batchRepo.save(b);

        recordEvent(b, InventoryEventType.CREATED, qty, qty, null, null, null, userId);
        return b;
    }

    /**
     * Deducts from a batch that the caller has locked {@code FOR UPDATE}. Rejects (409) rather than going negative.
     */
    public InventoryBatch deduct(InventoryBatch locked, BigDecimal amount, UUID userId, UUID recipeId, String reason) {
        requireActive(locked);
        BigDecimal delta = Units.qty(amount);
        if (delta == null || delta.signum() <= 0) {
            throw ApiException.validation("delta", "Amount to use must be greater than 0.");
        }
        if (locked.getCurrentQuantity().compareTo(delta) < 0) {
            throw ApiException.conflict("Insufficient quantity. Current: " + plain(locked.getCurrentQuantity()) + " "
                    + locked.getUnit().dbValue() + ", requested: " + plain(delta) + ".");
        }
        BigDecimal after = locked.getCurrentQuantity().subtract(delta);
        locked.setCurrentQuantity(after);
        InventoryEventType type = InventoryEventType.CONSUMED_PARTIALLY;
        if (after.signum() == 0) {
            locked.setStatus(BatchStatus.CONSUMED);
            type = InventoryEventType.CONSUMED_FULLY;
        }
        batchRepo.save(locked);
        recordEvent(locked, type, delta.negate(), after, reason, recipeId, null, userId);
        return locked;
    }

    /** Sets an absolute quantity (stock correction). Cannot exceed the initial quantity (chk_current_lte_initial). */
    public InventoryBatch setQuantity(InventoryBatch locked, BigDecimal quantity, UUID userId, String reason) {
        requireActive(locked);
        BigDecimal q = Units.qty(quantity);
        if (q == null || q.signum() < 0) {
            throw ApiException.validation("quantity", "Quantity cannot be negative.");
        }
        if (q.compareTo(locked.getInitialQuantity()) > 0) {
            throw ApiException.validation("quantity", "Quantity cannot exceed the original " + plain(locked.getInitialQuantity())
                    + " " + locked.getUnit().dbValue() + ". Add a new batch for extra stock.");
        }
        BigDecimal delta = q.subtract(locked.getCurrentQuantity());
        if (delta.signum() == 0) {
            return locked;
        }
        locked.setCurrentQuantity(q);
        if (q.signum() == 0) {
            locked.setStatus(BatchStatus.CONSUMED);
        }
        batchRepo.save(locked);
        recordEvent(locked, q.signum() == 0 ? InventoryEventType.CONSUMED_FULLY : InventoryEventType.QUANTITY_ADJUSTED,
                delta, q, reason, null, null, userId);
        return locked;
    }

    /** Marks the whole remaining quantity as consumed. */
    public InventoryBatch consumeAll(InventoryBatch locked, UUID userId, String reason) {
        requireActive(locked);
        return deduct(locked, locked.getCurrentQuantity(), userId, null, reason);
    }

    /**
     * Discards some or all of a batch and logs the rupee loss (PRD FR-ANA-002):
     * {@code loss = cost_inr x quantity_discarded / initial_quantity}.
     */
    public FoodWasteLog discard(InventoryBatch locked, BigDecimal quantity, WasteReason reason, UUID userId) {
        requireActive(locked);
        BigDecimal qty = quantity == null ? locked.getCurrentQuantity() : Units.qty(quantity);
        if (qty.signum() <= 0) {
            throw ApiException.validation("quantity", "Quantity to discard must be greater than 0.");
        }
        if (qty.compareTo(locked.getCurrentQuantity()) > 0) {
            throw ApiException.conflict("Cannot discard more than the remaining " + plain(locked.getCurrentQuantity()) + " "
                    + locked.getUnit().dbValue() + ".");
        }
        WasteReason why = reason != null ? reason
                : ExpiryClassifier.daysUntil(locked.getExpiryDate(), clock.today()) < 0 ? WasteReason.EXPIRED : WasteReason.SPOILED;

        BigDecimal loss = BigDecimal.ZERO;
        if (locked.getCostInr() != null && locked.getInitialQuantity().signum() > 0) {
            loss = locked.getCostInr().multiply(qty).divide(locked.getInitialQuantity(), 2, RoundingMode.HALF_UP);
        }
        FoodWasteLog log = new FoodWasteLog();
        log.setKitchenId(locked.getKitchenId());
        log.setUserId(userId);
        log.setBatchId(locked.getId());
        log.setItemName(locked.getCustomItemName());
        log.setCategoryId(locked.getCategoryId());
        log.setQuantityWasted(qty);
        log.setUnit(locked.getUnit());
        log.setEstimatedLossInr(loss);
        log.setWasteReason(why);
        log = wasteRepo.save(log);

        BigDecimal after = locked.getCurrentQuantity().subtract(qty);
        locked.setCurrentQuantity(after);
        if (after.signum() == 0) {
            locked.setStatus(why == WasteReason.SPOILED ? BatchStatus.SPOILED : BatchStatus.DISCARDED);
        }
        batchRepo.save(locked);
        InventoryEventType type = why == WasteReason.EXPIRED ? InventoryEventType.DISCARDED_EXPIRED : InventoryEventType.DISCARDED_SPOILED;
        recordEvent(locked, type, qty.negate(), after, why.dbValue(), null, log.getId(), userId);
        return log;
    }

    public InventoryBatch moveLocation(InventoryBatch locked, StorageLocation to, LocalDate newExpiry, UUID userId) {
        requireActive(locked);
        if (to == null) {
            throw ApiException.validation("location", "Location is required.");
        }
        StorageLocation from = locked.getStorageLocation();
        if (newExpiry != null) {
            if (newExpiry.isBefore(locked.getPurchaseDate())) {
                throw ApiException.validation("expiryDate", "Expiry date cannot be before the purchase date.");
            }
            locked.setExpiryDate(newExpiry);
            locked.setExpiryEstimated(false);
        } else if (locked.getCatalogItemId() != null) {
            // Recalculate only from validated guidance for the new location; otherwise keep the user's date.
            catalog.estimateExpiry(locked.getCatalogItemId(), to, clock.today()).ifPresent(d -> {
                locked.setExpiryDate(d);
                locked.setExpiryEstimated(true);
            });
        }
        locked.setStorageLocation(to);
        batchRepo.save(locked);
        recordEvent(locked, InventoryEventType.LOCATION_MOVED, BigDecimal.ZERO, locked.getCurrentQuantity(),
                from.dbValue() + " -> " + to.dbValue(), null, null, userId);
        return locked;
    }

    public record PurchaseLine(InventoryBatch batch, BigDecimal priceInr) {
    }

    /** Records a shopping trip for spend analytics (only lines with a price are included). */
    public GroceryPurchase recordPurchase(UUID kitchenId, UUID userId, LocalDate date, String storeName, UUID scanSessionId,
                                          String imageUrl, List<PurchaseLine> lines) {
        List<PurchaseLine> priced = lines.stream().filter(l -> l.priceInr() != null).toList();
        if (priced.isEmpty()) {
            return null;
        }
        BigDecimal total = priced.stream().map(PurchaseLine::priceInr).reduce(BigDecimal.ZERO, BigDecimal::add);
        GroceryPurchase p = new GroceryPurchase();
        p.setKitchenId(kitchenId);
        p.setUserId(userId);
        p.setPurchaseDate(date == null ? clock.today() : date);
        p.setStoreName(trimTo(storeName, 100));
        p.setTotalAmountInr(Units.money(total));
        p.setReceiptScanSessionId(scanSessionId);
        p.setReceiptImageUrl(imageUrl);
        p = purchaseRepo.save(p);
        for (PurchaseLine l : priced) {
            InventoryBatch b = l.batch();
            PurchaseItem item = new PurchaseItem();
            item.setPurchaseId(p.getId());
            item.setCatalogItemId(b.getCatalogItemId());
            item.setItemName(b.getCustomItemName());
            item.setCategoryId(b.getCategoryId());
            item.setQuantity(b.getInitialQuantity());
            item.setUnit(b.getUnit());
            item.setPriceInr(Units.money(l.priceInr()));
            item.setAssociatedBatchId(b.getId());
            purchaseItemRepo.save(item);
        }
        return p;
    }

    private void recordEvent(InventoryBatch b, InventoryEventType type, BigDecimal delta, BigDecimal after, String reason,
                             UUID recipeId, UUID wasteLogId, UUID userId) {
        InventoryEvent e = new InventoryEvent();
        e.setBatchId(b.getId());
        e.setKitchenId(b.getKitchenId());
        e.setEventType(type);
        e.setQuantityDelta(Units.qty(delta));
        e.setQuantityAfter(Units.qty(after));
        e.setReason(trimTo(reason, 500));
        e.setRecipeId(recipeId);
        e.setWasteLogId(wasteLogId);
        e.setPerformedByUserId(userId);
        eventRepo.save(e);
    }

    private static void requireActive(InventoryBatch b) {
        if (b.getStatus() != BatchStatus.ACTIVE) {
            throw ApiException.conflict("This item is no longer active (" + b.getStatus().dbValue() + ").");
        }
    }

    static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static String trimTo(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
