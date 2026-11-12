package com.foodwise.kitchen;

import com.foodwise.common.ApiException;
import com.foodwise.common.enums.KitchenRole;
import com.foodwise.security.KitchenAuthGuard;
import com.foodwise.user.User;
import com.foodwise.user.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kitchens and direct membership records. Invitation links are out of MVP scope (PRD section 11):
 * owners add flatmates who already have a FoodWise account by email.
 */
@Service
public class KitchenService {

    private final KitchenRepository kitchenRepo;
    private final KitchenMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final KitchenAuthGuard guard;
    private final JdbcTemplate jdbc;

    public KitchenService(KitchenRepository kitchenRepo, KitchenMemberRepository memberRepo, UserRepository userRepo,
                          KitchenAuthGuard guard, JdbcTemplate jdbc) {
        this.kitchenRepo = kitchenRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.guard = guard;
        this.jdbc = jdbc;
    }

    public record KitchenDto(UUID id, String name, KitchenRole role, long memberCount, Instant createdAt) {
    }

    public record MemberDto(UUID userId, String email, String firstName, String lastName, KitchenRole role, Instant joinedAt) {
    }

    public record KitchenNameRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record AddMemberRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    @Transactional(readOnly = true)
    public List<KitchenDto> listForUser(UUID userId) {
        Map<UUID, KitchenMember> memberships = memberRepo.findByUserId(userId).stream()
                .collect(Collectors.toMap(KitchenMember::getKitchenId, Function.identity()));
        return kitchenRepo.findAllForUser(userId).stream()
                .map(k -> toDto(k, memberships.get(k.getId()).getRole()))
                .toList();
    }

    /** Creates an additional kitchen. The first kitchen is always created during provisioning (Roadmap 1.F7). */
    @Transactional
    public KitchenDto create(UUID userId, KitchenNameRequest req) {
        if (memberRepo.findByUserId(userId).size() >= 10) {
            throw ApiException.conflict("You can belong to at most 10 kitchens.");
        }
        Kitchen kitchen = new Kitchen();
        kitchen.setName(req.name().trim());
        kitchen.setCreatedByUserId(userId);
        kitchen = kitchenRepo.save(kitchen);
        KitchenMember member = new KitchenMember();
        member.setKitchenId(kitchen.getId());
        member.setUserId(userId);
        member.setRole(KitchenRole.OWNER);
        memberRepo.save(member);
        return toDto(kitchen, KitchenRole.OWNER);
    }

    @Transactional
    public KitchenDto rename(UUID kitchenId, UUID userId, KitchenNameRequest req) {
        KitchenMember member = guard.requireMembership(kitchenId, userId);
        Kitchen kitchen = kitchenRepo.findById(kitchenId).orElseThrow(() -> ApiException.notFound("Kitchen not found."));
        kitchen.setName(req.name().trim());
        return toDto(kitchenRepo.save(kitchen), member.getRole());
    }

    @Transactional(readOnly = true)
    public List<MemberDto> members(UUID kitchenId, UUID userId) {
        guard.requireMembership(kitchenId, userId);
        List<KitchenMember> members = memberRepo.findByKitchenIdOrderByJoinedAt(kitchenId);
        Map<UUID, User> users = userRepo.findAllById(members.stream().map(KitchenMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return members.stream().map(m -> {
            User u = users.get(m.getUserId());
            return new MemberDto(m.getUserId(), u.getEmail(), u.getFirstName(), u.getLastName(), m.getRole(), m.getJoinedAt());
        }).toList();
    }

    @Transactional
    public List<MemberDto> addMember(UUID kitchenId, UUID userId, AddMemberRequest req) {
        guard.requireOwner(kitchenId, userId);
        User invitee = userRepo.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> ApiException.notFound(
                        "No FoodWise account uses that email yet. Ask your flatmate to sign up first, then add them."));
        if (memberRepo.findByKitchenIdAndUserId(kitchenId, invitee.getId()).isPresent()) {
            throw ApiException.conflict("That person is already a member of this kitchen.");
        }
        KitchenMember member = new KitchenMember();
        member.setKitchenId(kitchenId);
        member.setUserId(invitee.getId());
        member.setRole(KitchenRole.MEMBER);
        memberRepo.save(member);
        return members(kitchenId, userId);
    }

    /** Owners remove members; any member may remove themselves (leave). The last owner cannot leave. */
    @Transactional
    public void removeMember(UUID kitchenId, UUID actingUserId, UUID targetUserId) {
        KitchenMember acting = guard.requireMembership(kitchenId, actingUserId);
        boolean self = actingUserId.equals(targetUserId);
        if (!self && acting.getRole() != KitchenRole.OWNER) {
            throw ApiException.forbidden("Only the kitchen owner can remove members.");
        }
        KitchenMember target = memberRepo.findByKitchenIdAndUserId(kitchenId, targetUserId)
                .orElseThrow(() -> ApiException.notFound("Member not found."));
        if (target.getRole() == KitchenRole.OWNER && memberRepo.countByKitchenIdAndRole(kitchenId, KitchenRole.OWNER) <= 1) {
            throw ApiException.conflict("A kitchen needs at least one owner.");
        }
        if (self && memberRepo.findByUserId(actingUserId).size() <= 1) {
            throw ApiException.conflict("You cannot leave your only kitchen.");
        }
        memberRepo.delete(target);
    }

    /** Settings > Data Management > Reset Kitchen Data. Owner only; keeps the kitchen and its members. */
    @Transactional
    public void resetData(UUID kitchenId, UUID userId) {
        guard.requireOwner(kitchenId, userId);
        jdbc.update("DELETE FROM notifications WHERE kitchen_id = ?", kitchenId);
        jdbc.update("DELETE FROM meal_plans WHERE kitchen_id = ?", kitchenId);
        jdbc.update("DELETE FROM shopping_lists WHERE kitchen_id = ?", kitchenId);
        jdbc.update("DELETE FROM food_waste_logs WHERE kitchen_id = ?", kitchenId);
        jdbc.update("DELETE FROM grocery_purchases WHERE kitchen_id = ?", kitchenId);
        jdbc.update("DELETE FROM inventory_batches WHERE kitchen_id = ?", kitchenId);
        jdbc.update("DELETE FROM scan_sessions WHERE kitchen_id = ?", kitchenId);
    }

    private KitchenDto toDto(Kitchen k, KitchenRole role) {
        return new KitchenDto(k.getId(), k.getName(), role, memberRepo.countByKitchenId(k.getId()), k.getCreatedAt());
    }
}
