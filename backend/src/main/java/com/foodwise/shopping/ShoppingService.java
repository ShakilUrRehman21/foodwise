package com.foodwise.shopping;

import com.foodwise.catalog.CatalogItemDto;
import com.foodwise.catalog.CatalogService;
import com.foodwise.catalog.CategoryDto;
import com.foodwise.common.ApiException;
import com.foodwise.common.AppClock;
import com.foodwise.common.Units;
import com.foodwise.common.enums.AddedReason;
import com.foodwise.common.enums.ShoppingListStatus;
import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.Unit;
import com.foodwise.inventory.BatchDto;
import com.foodwise.inventory.BatchMapper;
import com.foodwise.inventory.InventoryBatch;
import com.foodwise.inventory.InventoryOperations;
import com.foodwise.inventory.InventoryService;
import com.foodwise.recipe.RecipeCatalog;
import com.foodwise.recipe.RecipeIngredient;
import com.foodwise.security.KitchenAuthGuard;
import jakarta.validation.Valid;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Smart shopping list (Roadmap M7): one active list per kitchen, no duplicate unchecked items. */
@Service
public class ShoppingService {

    private final ShoppingListRepository listRepo;
    private final ShoppingListItemRepository itemRepo;
    private final CatalogService catalog;
    private final RecipeCatalog recipes;
    private final InventoryService inventory;
    private final InventoryOperations ops;
    private final BatchMapper mapper;
    private final KitchenAuthGuard guard;
    private final AppClock clock;

    public ShoppingService(ShoppingListRepository listRepo, ShoppingListItemRepository itemRepo, CatalogService catalog,
                           RecipeCatalog recipes, InventoryService inventory, InventoryOperations ops, BatchMapper mapper,
                           KitchenAuthGuard guard, AppClock clock) {
        this.listRepo = listRepo;
        this.itemRepo = itemRepo;
        this.catalog = catalog;
        this.recipes = recipes;
        this.inventory = inventory;
        this.ops = ops;
        this.mapper = mapper;
        this.guard = guard;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ DTOs

    public record ItemDto(UUID id, UUID catalogItemId, String name, UUID categoryId, String categorySlug, String categoryName,
                          BigDecimal quantity, Unit unit, BigDecimal estimatedCostInr, boolean checked, Instant checkedAt,
                          AddedReason addedReason, UUID linkedRecipeId, String linkedRecipeTitle,
                          StorageLocation suggestedLocation, Integer shelfLifeDays) {
    }

    public record ListDto(UUID id, String name, List<ItemDto> items, int checkedCount, BigDecimal estimatedTotalInr) {
    }

    public record AddItemRequest(UUID catalogItemId, @Size(max = 100) String name, UUID categoryId,
                                 @Positive @DecimalMax("99999") BigDecimal quantity, Unit unit,
                                 @DecimalMin("0") @DecimalMax("9999999") BigDecimal estimatedCostInr,
                                 AddedReason addedReason, UUID linkedRecipeId) {
    }

    public record AddItemsRequest(@NotEmpty @Size(max = 50) List<@Valid AddItemRequest> items) {
    }

    public record AddResult(ListDto list, int added, int alreadyOnList) {
    }

    public record UpdateItemRequest(@Size(min = 1, max = 100) String name, @Positive @DecimalMax("99999") BigDecimal quantity,
                                    Unit unit, @DecimalMin("0") @DecimalMax("9999999") BigDecimal estimatedCostInr, UUID categoryId) {
    }

    public record CheckRequest(Boolean checked) {
    }

    public record ImportLine(@NotNull UUID itemId, StorageLocation storageLocation, LocalDate expiryDate,
                             @Positive @DecimalMax("99999") BigDecimal quantity, Unit unit,
                             @DecimalMin("0") @DecimalMax("9999999") BigDecimal costInr) {
    }

    public record ImportRequest(@NotEmpty @Size(max = 100) List<@Valid ImportLine> items, @Size(max = 100) String storeName) {
    }

    public record ImportResult(List<BatchDto> created, ListDto list) {
    }

    // ------------------------------------------------------------------ queries

    @Transactional
    public ListDto getActive(UUID kitchenId, UUID userId) {
        guard.requireMembership(kitchenId, userId);
        return toDto(activeList(kitchenId));
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public AddResult addItems(UUID kitchenId, UUID userId, List<AddItemRequest> requests) {
        guard.requireMembership(kitchenId, userId);
        ShoppingList list = activeList(kitchenId);
        List<ShoppingListItem> existing = itemRepo.findByShoppingListIdOrderByCreatedAt(list.getId());
        int added = 0;
        int dupes = 0;
        for (AddItemRequest req : requests) {
            CatalogItemDto item = req.catalogItemId() == null ? null : catalog.requireItem(req.catalogItemId());
            String name = req.name() != null && !req.name().isBlank() ? req.name().trim() : item == null ? null : item.nameEn();
            if (name == null) {
                throw ApiException.validation("name", "Item name is required.");
            }
            if (existing.stream().anyMatch(e -> !e.isChecked() && sameItem(e, item, name))) {
                dupes++;
                continue;
            }
            Unit unit = req.unit() != null ? req.unit() : item != null ? item.defaultUnit() : Unit.PCS;
            if (!Units.isInventoryUnit(unit)) {
                unit = item != null ? item.defaultUnit() : Unit.PCS;
            }
            ShoppingListItem si = new ShoppingListItem();
            si.setShoppingListId(list.getId());
            si.setCatalogItemId(item == null ? null : item.id());
            si.setItemName(name);
            si.setCategoryId(req.categoryId() != null ? req.categoryId() : item == null ? null : item.categoryId());
            si.setQuantity(Units.qty(req.quantity() == null ? BigDecimal.ONE : req.quantity()));
            si.setUnit(unit);
            si.setEstimatedCostInr(Units.money(req.estimatedCostInr()));
            si.setChecked(false);
            si.setAddedReason(req.addedReason() == null ? AddedReason.MANUAL : req.addedReason());
            si.setLinkedRecipeId(req.linkedRecipeId());
            existing.add(itemRepo.save(si));
            added++;
        }
        touch(list);
        return new AddResult(toDto(list), added, dupes);
    }

    /** One-click "Add missing ingredients" from a recipe (FR-REC-002 / Roadmap 5.F5). */
    @Transactional
    public AddResult addMissingFromRecipe(UUID kitchenId, UUID userId, UUID recipeId, List<UUID> catalogItemIds) {
        guard.requireMembership(kitchenId, userId);
        RecipeCatalog.Entry e = recipes.byId(recipeId).orElseThrow(() -> ApiException.notFound("Recipe not found."));
        Set<UUID> wanted = catalogItemIds == null ? Set.of() : Set.copyOf(catalogItemIds);
        List<AddItemRequest> reqs = new ArrayList<>();
        for (RecipeIngredient ing : e.ingredients()) {
            if (ing.getCatalogItemId() == null || ing.isPantryStaple() || !wanted.contains(ing.getCatalogItemId())) {
                continue;
            }
            CatalogItemDto item = catalog.requireItem(ing.getCatalogItemId());
            BigDecimal qty = Units.convert(ing.getQuantity(), ing.getUnit(), item.defaultUnit()).orElse(BigDecimal.ONE);
            Unit unit = Units.compatible(ing.getUnit(), item.defaultUnit()) ? item.defaultUnit() : item.defaultUnit();
            reqs.add(new AddItemRequest(item.id(), null, null, qty.signum() > 0 ? qty : BigDecimal.ONE, unit, null,
                    AddedReason.RECIPE_MISSING, recipeId));
        }
        if (reqs.isEmpty()) {
            throw ApiException.validation("catalogItemIds", "None of those ingredients belong to this recipe.");
        }
        return addItems(kitchenId, userId, reqs);
    }

    /** Auto-replenish: add everything currently low or out of stock (FR-SHP-001). */
    @Transactional
    public AddResult addLowStock(UUID kitchenId, UUID userId) {
        guard.requireMembership(kitchenId, userId);
        List<AddItemRequest> reqs = inventory.lowStockInternal(kitchenId).stream()
                .map(l -> new AddItemRequest(l.catalogItemId(), l.name(), l.categoryId(), l.suggestedQuantity(), l.unit(), null,
                        AddedReason.AUTO_LOW_STOCK, null))
                .toList();
        if (reqs.isEmpty()) {
            return new AddResult(getActive(kitchenId, userId), 0, 0);
        }
        return addItems(kitchenId, userId, reqs);
    }

    @Transactional
    public ListDto updateItem(UUID kitchenId, UUID userId, UUID itemId, UpdateItemRequest req) {
        guard.requireMembership(kitchenId, userId);
        ShoppingList list = activeList(kitchenId);
        ShoppingListItem item = findItem(list, itemId);
        if (req.name() != null) {
            item.setItemName(req.name().trim());
        }
        if (req.quantity() != null) {
            item.setQuantity(Units.qty(req.quantity()));
        }
        if (req.unit() != null) {
            if (!Units.isInventoryUnit(req.unit())) {
                throw ApiException.validation("unit", "Unit must be one of g, kg, ml, L, pcs, pkt, bunch.");
            }
            item.setUnit(req.unit());
        }
        if (req.estimatedCostInr() != null) {
            item.setEstimatedCostInr(Units.money(req.estimatedCostInr()));
        }
        if (req.categoryId() != null) {
            catalog.category(req.categoryId()).orElseThrow(() -> ApiException.validation("categoryId", "Unknown category."));
            item.setCategoryId(req.categoryId());
        }
        itemRepo.save(item);
        touch(list);
        return toDto(list);
    }

    @Transactional
    public ListDto check(UUID kitchenId, UUID userId, UUID itemId, CheckRequest req) {
        guard.requireMembership(kitchenId, userId);
        ShoppingList list = activeList(kitchenId);
        ShoppingListItem item = findItem(list, itemId);
        boolean checked = req == null || req.checked() == null ? !item.isChecked() : req.checked();
        item.setChecked(checked);
        item.setCheckedAt(checked ? Instant.now() : null);
        item.setCheckedByUserId(checked ? userId : null);
        itemRepo.save(item);
        touch(list);
        return toDto(list);
    }

    @Transactional
    public ListDto deleteItem(UUID kitchenId, UUID userId, UUID itemId) {
        guard.requireMembership(kitchenId, userId);
        ShoppingList list = activeList(kitchenId);
        itemRepo.delete(findItem(list, itemId));
        touch(list);
        return toDto(list);
    }

    @Transactional
    public ListDto clearChecked(UUID kitchenId, UUID userId) {
        guard.requireMembership(kitchenId, userId);
        ShoppingList list = activeList(kitchenId);
        itemRepo.deleteAll(itemRepo.findByShoppingListIdOrderByCreatedAt(list.getId()).stream().filter(ShoppingListItem::isChecked).toList());
        touch(list);
        return toDto(list);
    }

    /**
     * Moves checked items into inventory (FR-SHP-004) in one transaction. Expiry dates come from the user unless
     * validated catalog guidance exists; nothing is ever invented. Imported items leave the list; the list is
     * archived only when it becomes empty, so unchecked items are never lost.
     */
    @Transactional
    public ImportResult importToInventory(UUID kitchenId, UUID userId, UUID listId, ImportRequest req) {
        guard.requireMembership(kitchenId, userId);
        ShoppingList list = listRepo.findByIdAndKitchenId(listId, kitchenId)
                .filter(l -> l.getStatus() == ShoppingListStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Shopping list not found."));
        Map<UUID, ShoppingListItem> items = itemRepo.findByShoppingListIdOrderByCreatedAt(list.getId()).stream()
                .collect(Collectors.toMap(ShoppingListItem::getId, i -> i));
        List<BatchDto> created = new ArrayList<>();
        List<InventoryOperations.PurchaseLine> purchase = new ArrayList<>();
        int index = 0;
        Set<UUID> seen = new java.util.HashSet<>();
        for (ImportLine line : req.items()) {
            ShoppingListItem si = items.get(line.itemId());
            if (si == null || !seen.add(line.itemId())) {
                throw ApiException.validation("items[" + index + "].itemId", "Item is not on this list.");
            }
            if (!si.isChecked()) {
                throw ApiException.validation("items[" + index + "].itemId", si.getItemName() + " is not checked off yet.");
            }
            BigDecimal cost = line.costInr() != null ? line.costInr() : si.getEstimatedCostInr();
            InventoryBatch b = ops.createBatch(new InventoryOperations.NewBatch(kitchenId, si.getCatalogItemId(), si.getItemName(),
                    si.getCategoryId(), line.storageLocation(), line.quantity() != null ? line.quantity() : si.getQuantity(),
                    line.unit() != null ? line.unit() : si.getUnit(), clock.today(), line.expiryDate(), cost,
                    false, null, null, null), userId, "items[" + index + "].");
            purchase.add(new InventoryOperations.PurchaseLine(b, cost));
            created.add(mapper.toDto(b));
            itemRepo.delete(si);
            index++;
        }
        ops.recordPurchase(kitchenId, userId, clock.today(), req.storeName(), null, null, purchase);
        itemRepo.flush();
        if (itemRepo.countByShoppingListId(list.getId()) == 0) {
            list.setStatus(ShoppingListStatus.ARCHIVED);
            listRepo.saveAndFlush(list);
            list = activeList(kitchenId);
        } else {
            touch(list);
        }
        return new ImportResult(created, toDto(list));
    }

    // ------------------------------------------------------------------ helpers

    private ShoppingList activeList(UUID kitchenId) {
        Optional<ShoppingList> list = listRepo.findFirstByKitchenIdAndStatus(kitchenId, ShoppingListStatus.ACTIVE);
        if (list.isPresent()) {
            return list.get();
        }
        listRepo.insertActiveIfAbsent(kitchenId);
        return listRepo.findFirstByKitchenIdAndStatus(kitchenId, ShoppingListStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active shopping list missing after insert"));
    }

    private ShoppingListItem findItem(ShoppingList list, UUID itemId) {
        return itemRepo.findByIdAndShoppingListId(itemId, list.getId()).orElseThrow(() -> ApiException.notFound("Item not found."));
    }

    private void touch(ShoppingList list) {
        list.setUpdatedAt(Instant.now());
        listRepo.save(list);
    }

    private static boolean sameItem(ShoppingListItem e, CatalogItemDto item, String name) {
        if (item != null && item.id().equals(e.getCatalogItemId())) {
            return true;
        }
        return e.getItemName().trim().toLowerCase(Locale.ROOT).equals(name.trim().toLowerCase(Locale.ROOT));
    }

    private ListDto toDto(ShoppingList list) {
        List<ShoppingListItem> items = itemRepo.findByShoppingListIdOrderByCreatedAt(list.getId());
        Map<UUID, String> recipeTitles = new HashMap<>();
        items.stream().map(ShoppingListItem::getLinkedRecipeId).filter(Objects::nonNull).distinct()
                .forEach(id -> recipes.byId(id).ifPresent(r -> recipeTitles.put(id, r.recipe().getTitle())));
        List<ItemDto> dtos = items.stream().map(i -> {
            CategoryDto cat = catalog.category(i.getCategoryId()).orElse(null);
            CatalogItemDto ci = catalog.item(i.getCatalogItemId()).orElse(null);
            StorageLocation loc = ci == null ? StorageLocation.FRIDGE : ci.defaultLocation();
            return new ItemDto(i.getId(), i.getCatalogItemId(), i.getItemName(), i.getCategoryId(), cat == null ? null : cat.slug(),
                    cat == null ? null : cat.name(), i.getQuantity(), i.getUnit(), i.getEstimatedCostInr(), i.isChecked(),
                    i.getCheckedAt(), i.getAddedReason(), i.getLinkedRecipeId(), recipeTitles.get(i.getLinkedRecipeId()),
                    loc, ci == null ? null : ci.shelfLifeFor(loc));
        }).toList();
        BigDecimal total = items.stream().map(ShoppingListItem::getEstimatedCostInr).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int checked = (int) items.stream().filter(ShoppingListItem::isChecked).count();
        return new ListDto(list.getId(), list.getName(), dtos, checked, Units.money(total));
    }
}
