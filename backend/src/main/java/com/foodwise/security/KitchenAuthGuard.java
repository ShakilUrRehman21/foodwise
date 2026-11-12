package com.foodwise.security;

import com.foodwise.common.ApiException;
import com.foodwise.common.enums.KitchenRole;
import com.foodwise.kitchen.KitchenMember;
import com.foodwise.kitchen.KitchenMemberRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kitchen-level tenancy check (SYSTEM_DESIGN 4.4). Called from the service layer on every kitchen-scoped operation
 * so isolation cannot be bypassed by internal callers. Non-members get 403 (never 404) so IDs are not probed.
 */
@Component
public class KitchenAuthGuard {

    private final KitchenMemberRepository memberRepo;

    public KitchenAuthGuard(KitchenMemberRepository memberRepo) {
        this.memberRepo = memberRepo;
    }

    public KitchenMember requireMembership(UUID kitchenId, UUID userId) {
        if (kitchenId == null || userId == null) {
            throw ApiException.forbidden("You are not a member of this kitchen.");
        }
        return memberRepo.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this kitchen."));
    }

    public KitchenMember requireOwner(UUID kitchenId, UUID userId) {
        KitchenMember member = requireMembership(kitchenId, userId);
        if (member.getRole() != KitchenRole.OWNER) {
            throw ApiException.forbidden("Only the kitchen owner can do this.");
        }
        return member;
    }
}
