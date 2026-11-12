package com.foodwise.leftover;

import com.foodwise.common.ApiException;
import com.foodwise.common.AppClock;
import com.foodwise.common.enums.LeftoverType;
import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.Unit;
import com.foodwise.common.enums.WasteReason;
import com.foodwise.inventory.BatchDto;
import com.foodwise.inventory.BatchMapper;
import com.foodwise.inventory.InventoryBatch;
import com.foodwise.inventory.InventoryBatchRepository;
import com.foodwise.inventory.InventoryOperations;
import com.foodwise.recipe.RecipeRepository;
import com.foodwise.security.KitchenAuthGuard;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cooked-food tracking (Roadmap M6). No leftover storage guidance has been validated/approved yet (FR-LFT-002),
 * so the user always supplies the expiry date; the app only shows date-level indicators, never hour countdowns.
 */
@Service
public class LeftoverService {

    private final InventoryBatchRepository batchRepo;
    private final InventoryOperations ops;
    private final BatchMapper mapper;
    private final LeftoverRuleRepository ruleRepo;
    private final RecipeRepository recipeRepo;
    private final KitchenAuthGuard guard;
    private final AppClock clock;

    public LeftoverService(InventoryBatchRepository batchRepo, InventoryOperations ops, BatchMapper mapper,
                           LeftoverRuleRepository ruleRepo, RecipeRepository recipeRepo, KitchenAuthGuard guard, AppClock clock) {
        this.batchRepo = batchRepo;
        this.ops = ops;
        this.mapper = mapper;
        this.ruleRepo = ruleRepo;
        this.recipeRepo = recipeRepo;
        this.guard = guard;
        this.clock = clock;
    }

    public record LogLeftoverRequest(
            @NotBlank @Size(max = 100) String dishName,
            @NotNull LeftoverType leftoverType,
            @NotNull @Positive @DecimalMax("99999") BigDecimal quantity,
            @NotNull Unit unit,
            Instant cookedAt,
            @NotNull LocalDate expiryDate,
            StorageLocation storageLocation,
            @Size(max = 300) String containerNote) {
    }

    public record RepurposeIdea(UUID id, String title, String description, List<String> steps, String recipeSlug) {
    }

    public record LeftoverDto(BatchDto batch, RepurposeIdea topIdea) {
    }

    public record ConsumeRequest(@Positive BigDecimal quantity) {
    }

    @Transactional
    public LeftoverDto log(UUID kitchenId, UUID userId, LogLeftoverRequest req) {
        guard.requireMembership(kitchenId, userId);
        Instant cookedAt = req.cookedAt() == null ? Instant.now() : req.cookedAt();
        if (cookedAt.isAfter(Instant.now().plusSeconds(300))) {
            throw ApiException.validation("cookedAt", "Cooked time cannot be in the future.");
        }
        LocalDate cookedDate = LocalDate.ofInstant(cookedAt, clock.zone());
        if (req.expiryDate().isBefore(clock.today())) {
            throw ApiException.validation("expiryDate", "Expiry date cannot be in the past.");
        }
        InventoryBatch b = ops.createBatch(new InventoryOperations.NewBatch(kitchenId, null, req.dishName(), null,
                req.storageLocation() == null ? StorageLocation.FRIDGE : req.storageLocation(), req.quantity(), req.unit(),
                cookedDate, req.expiryDate(), null, true, req.leftoverType(), cookedAt, req.containerNote()), userId, "");
        return toLeftover(b);
    }

    @Transactional(readOnly = true)
    public List<LeftoverDto> list(UUID kitchenId, UUID userId) {
        guard.requireMembership(kitchenId, userId);
        return batchRepo.findActiveLeftovers(kitchenId).stream().map(this::toLeftover).toList();
    }

    @Transactional(readOnly = true)
    public List<RepurposeIdea> repurpose(UUID kitchenId, UUID userId, UUID batchId) {
        guard.requireMembership(kitchenId, userId);
        InventoryBatch b = batchRepo.findByIdAndKitchenId(batchId, kitchenId)
                .filter(InventoryBatch::isLeftover)
                .orElseThrow(() -> ApiException.notFound("Leftover not found."));
        return ideas(b.getLeftoverType());
    }

    @Transactional
    public BatchDto consume(UUID kitchenId, UUID userId, UUID batchId, ConsumeRequest req) {
        guard.requireMembership(kitchenId, userId);
        InventoryBatch b = lockLeftover(kitchenId, batchId);
        InventoryBatch updated = req == null || req.quantity() == null
                ? ops.consumeAll(b, userId, "Leftover eaten")
                : ops.deduct(b, req.quantity(), userId, null, "Leftover partly eaten");
        return mapper.toDto(updated);
    }

    @Transactional
    public BatchDto discard(UUID kitchenId, UUID userId, UUID batchId) {
        guard.requireMembership(kitchenId, userId);
        InventoryBatch b = lockLeftover(kitchenId, batchId);
        ops.discard(b, null, WasteReason.FORGOTTEN_LEFTOVER, userId);
        return mapper.toDto(b);
    }

    private InventoryBatch lockLeftover(UUID kitchenId, UUID batchId) {
        return batchRepo.findByIdAndKitchenIdForUpdate(batchId, kitchenId)
                .filter(InventoryBatch::isLeftover)
                .orElseThrow(() -> ApiException.notFound("Leftover not found."));
    }

    private List<RepurposeIdea> ideas(LeftoverType type) {
        List<LeftoverRule> rules = ruleRepo.findByLeftoverTypeOrderByDisplayOrder(type == null ? LeftoverType.OTHER : type);
        if (rules.isEmpty()) {
            rules = ruleRepo.findByLeftoverTypeOrderByDisplayOrder(LeftoverType.OTHER);
        }
        return rules.stream().map(r -> new RepurposeIdea(r.getId(), r.getTitle(), r.getDescription(), r.getInstructions(),
                r.getRecipeId() == null ? null : recipeRepo.findById(r.getRecipeId()).map(x -> x.getSlug()).orElse(null))).toList();
    }

    private LeftoverDto toLeftover(InventoryBatch b) {
        List<RepurposeIdea> ideas = ideas(b.getLeftoverType());
        return new LeftoverDto(mapper.toDto(b), ideas.isEmpty() ? null : ideas.getFirst());
    }

}
