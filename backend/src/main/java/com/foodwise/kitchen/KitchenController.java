package com.foodwise.kitchen;

import com.foodwise.common.ApiResponse;
import com.foodwise.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchens")
public class KitchenController {

    private final KitchenService kitchenService;

    public KitchenController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    @GetMapping
    public ApiResponse<List<KitchenService.KitchenDto>> list(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(kitchenService.listForUser(user.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KitchenService.KitchenDto> create(@AuthenticationPrincipal CurrentUser user,
                                                         @Valid @RequestBody KitchenService.KitchenNameRequest req) {
        return ApiResponse.ok(kitchenService.create(user.id(), req), "Kitchen created.");
    }

    @PatchMapping("/{kitchenId}")
    public ApiResponse<KitchenService.KitchenDto> rename(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                         @Valid @RequestBody KitchenService.KitchenNameRequest req) {
        return ApiResponse.ok(kitchenService.rename(kitchenId, user.id(), req), "Kitchen updated.");
    }

    @GetMapping("/{kitchenId}/members")
    public ApiResponse<List<KitchenService.MemberDto>> members(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        return ApiResponse.ok(kitchenService.members(kitchenId, user.id()));
    }

    @PostMapping("/{kitchenId}/members")
    public ApiResponse<List<KitchenService.MemberDto>> addMember(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                                 @Valid @RequestBody KitchenService.AddMemberRequest req) {
        return ApiResponse.ok(kitchenService.addMember(kitchenId, user.id(), req), "Member added.");
    }

    @DeleteMapping("/{kitchenId}/members/{userId}")
    public ApiResponse<Void> removeMember(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                          @PathVariable UUID userId) {
        kitchenService.removeMember(kitchenId, user.id(), userId);
        return ApiResponse.ok(null, "Member removed.");
    }

    @PostMapping("/{kitchenId}/reset")
    public ApiResponse<Void> reset(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId) {
        kitchenService.resetData(kitchenId, user.id());
        return ApiResponse.ok(null, "Kitchen data has been reset.");
    }
}
