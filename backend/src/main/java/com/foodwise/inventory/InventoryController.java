package com.foodwise.inventory;

import com.foodwise.common.ApiResponse;
import com.foodwise.common.enums.BatchStatus;
import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.WasteReason;
import com.foodwise.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchens/{kitchenId}/inventory")
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/batches")
    public ApiResponse<List<BatchDto>> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                            @RequestParam(required = false) StorageLocation location,
                                            @RequestParam(required = false) UUID categoryId,
                                            @RequestParam(required = false) BatchStatus status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) InventoryService.SortBy sort,
                                            @RequestParam(required = false) Boolean leftovers) {
        return ApiResponse.ok(inventory.list(kitchenId, user.id(),
                new InventoryService.ListFilter(location, categoryId, status, q, sort, leftovers)));
    }

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BatchDto> create(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                        @Valid @RequestBody InventoryService.CreateBatchRequest req) {
        return ApiResponse.ok(inventory.create(kitchenId, user.id(), req), "Item added.");
    }

    @GetMapping("/batches/{batchId}")
    public ApiResponse<BatchDto> get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                     @PathVariable UUID batchId) {
        return ApiResponse.ok(inventory.get(kitchenId, user.id(), batchId));
    }

    @PatchMapping("/batches/{batchId}")
    public ApiResponse<BatchDto> update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                        @PathVariable UUID batchId, @Valid @RequestBody InventoryService.UpdateBatchRequest req) {
        return ApiResponse.ok(inventory.update(kitchenId, user.id(), batchId, req), "Item updated.");
    }

    @PatchMapping("/batches/{batchId}/quantity")
    public ApiResponse<BatchDto> adjust(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                        @PathVariable UUID batchId, @Valid @RequestBody InventoryService.AdjustQuantityRequest req) {
        return ApiResponse.ok(inventory.adjustQuantity(kitchenId, user.id(), batchId, req), "Quantity updated.");
    }

    @PatchMapping("/batches/{batchId}/location")
    public ApiResponse<BatchDto> move(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                      @PathVariable UUID batchId, @Valid @RequestBody InventoryService.MoveLocationRequest req) {
        return ApiResponse.ok(inventory.moveLocation(kitchenId, user.id(), batchId, req), "Item moved.");
    }

    @PostMapping("/batches/{batchId}/consume")
    public ApiResponse<BatchDto> consume(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                         @PathVariable UUID batchId) {
        return ApiResponse.ok(inventory.consume(kitchenId, user.id(), batchId), "Marked as used up.");
    }

    /** Discard & log waste (soft: the batch row remains with status discarded/spoiled). */
    @DeleteMapping("/batches/{batchId}")
    public ApiResponse<InventoryService.DiscardResult> discard(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                               @PathVariable UUID batchId,
                                                               @RequestParam(required = false) WasteReason reason,
                                                               @RequestParam(required = false) BigDecimal quantity) {
        return ApiResponse.ok(inventory.discard(kitchenId, user.id(), batchId, reason, quantity), "Logged as waste.");
    }

    @PostMapping("/batches/bulk")
    public ApiResponse<List<BatchDto>> bulk(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                            @Valid @RequestBody InventoryService.BulkRequest req) {
        return ApiResponse.ok(inventory.bulk(kitchenId, user.id(), req), "Updated " + req.batchIds().size() + " items.");
    }

    @GetMapping("/batches/{batchId}/events")
    public ApiResponse<List<InventoryService.EventDto>> events(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                               @PathVariable UUID batchId) {
        return ApiResponse.ok(inventory.events(kitchenId, user.id(), batchId));
    }

    @GetMapping("/low-stock")
    public ApiResponse<List<InventoryService.LowStockItem>> lowStock(@AuthenticationPrincipal CurrentUser user,
                                                                     @PathVariable UUID kitchenId) {
        return ApiResponse.ok(inventory.lowStock(kitchenId, user.id()));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        byte[] body = inventory.exportCsv(kitchenId, user.id()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"foodwise-inventory.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
