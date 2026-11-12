package com.foodwise.user;

import com.foodwise.common.ApiException;
import com.foodwise.common.enums.DietaryPreference;
import com.foodwise.common.enums.KitchenRole;
import com.foodwise.kitchen.Kitchen;
import com.foodwise.kitchen.KitchenMember;
import com.foodwise.kitchen.KitchenMemberRepository;
import com.foodwise.kitchen.KitchenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * User lifecycle: authoritative webhook provisioning with an idempotent JIT fallback (SYSTEM_DESIGN 4.3),
 * profile and preference management.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    static final String PLACEHOLDER_EMAIL_DOMAIN = "@users.pending.foodwise";

    private final UserRepository userRepo;
    private final UserPreferencesRepository prefsRepo;
    private final KitchenRepository kitchenRepo;
    private final KitchenMemberRepository memberRepo;

    public UserService(UserRepository userRepo, UserPreferencesRepository prefsRepo,
                       KitchenRepository kitchenRepo, KitchenMemberRepository memberRepo) {
        this.userRepo = userRepo;
        this.prefsRepo = prefsRepo;
        this.kitchenRepo = kitchenRepo;
        this.memberRepo = memberRepo;
    }

    /** Identity attributes from Clerk (JWT claims or webhook payload) or from dev auth. */
    public record ExternalIdentity(String externalId, String email, String firstName, String lastName, String imageUrl) {
    }

    public Optional<User> findByExternalId(String externalId) {
        return userRepo.findByClerkId(externalId);
    }

    /**
     * Creates the user, their default kitchen (owner membership) and default preferences exactly once.
     * Safe to call concurrently from the webhook and from the first authenticated request.
     */
    @Transactional
    public User provisionUserIfAbsent(ExternalIdentity identity) {
        Optional<User> existing = userRepo.findByClerkId(identity.externalId());
        if (existing.isPresent()) {
            return refreshProfile(existing.get(), identity);
        }

        String email = normaliseEmail(identity.email(), identity.externalId());
        Optional<User> sameEmail = userRepo.findByEmailIgnoreCase(email);
        if (sameEmail.isPresent()) {
            // Same verified email, new identity-provider id (e.g. account re-created in Clerk): relink rather than fail.
            User user = sameEmail.get();
            log.warn("Relinking user {} from external id {} to {}", user.getId(), user.getClerkId(), identity.externalId());
            user.setClerkId(identity.externalId());
            return userRepo.save(user);
        }

        int created = userRepo.insertIfAbsent(identity.externalId(), email, trim(identity.firstName()),
                trim(identity.lastName()), identity.imageUrl());
        User user = userRepo.findByClerkId(identity.externalId())
                .orElseThrow(() -> new IllegalStateException("User row missing after idempotent insert"));
        if (created == 1) {
            createDefaultKitchenAndPreferences(user);
            log.info("Provisioned user {} with default kitchen", user.getId());
        }
        return user;
    }

    /** Applies webhook {@code user.updated} data. Unknown users are provisioned. */
    @Transactional
    public User syncFromIdentity(ExternalIdentity identity) {
        return userRepo.findByClerkId(identity.externalId())
                .map(u -> refreshProfile(u, identity))
                .orElseGet(() -> provisionUserIfAbsent(identity));
    }

    private User refreshProfile(User user, ExternalIdentity identity) {
        boolean changed = false;
        if (identity.email() != null && !identity.email().isBlank()) {
            String email = identity.email().trim().toLowerCase(Locale.ROOT);
            if (!email.equalsIgnoreCase(user.getEmail()) && !userRepo.existsByEmailIgnoreCase(email)) {
                user.setEmail(email);
                changed = true;
            }
        }
        if (identity.firstName() != null && user.getFirstName() == null) {
            user.setFirstName(trim(identity.firstName()));
            changed = true;
        }
        if (identity.lastName() != null && user.getLastName() == null) {
            user.setLastName(trim(identity.lastName()));
            changed = true;
        }
        if (identity.imageUrl() != null && !identity.imageUrl().equals(user.getImageUrl())) {
            user.setImageUrl(identity.imageUrl());
            changed = true;
        }
        return changed ? userRepo.save(user) : user;
    }

    private void createDefaultKitchenAndPreferences(User user) {
        Kitchen kitchen = new Kitchen();
        kitchen.setName(defaultKitchenName(user.getFirstName()));
        kitchen.setCreatedByUserId(user.getId());
        kitchen = kitchenRepo.save(kitchen);

        KitchenMember member = new KitchenMember();
        member.setKitchenId(kitchen.getId());
        member.setUserId(user.getId());
        member.setRole(KitchenRole.OWNER);
        memberRepo.save(member);

        UserPreferences prefs = new UserPreferences();
        prefs.setUserId(user.getId());
        prefs.setDietaryType(DietaryPreference.OMNIVORE);
        prefs.setCurrencyCode("INR");
        prefs.setCurrencySymbol("₹");
        prefs.setNotifyExpiryDaysBefore(3);
        prefs.setNotifyDailyDigestTime(LocalTime.of(8, 0));
        prefs.setEmailNotificationsEnabled(true);
        prefs.setPushNotificationsEnabled(true);
        prefsRepo.save(prefs);
    }

    static String defaultKitchenName(String firstName) {
        if (firstName == null || firstName.isBlank()) {
            return "My Kitchen";
        }
        String name = firstName.trim();
        String kitchen = name + (name.endsWith("s") ? "' Kitchen" : "'s Kitchen");
        return kitchen.length() > 100 ? kitchen.substring(0, 100) : kitchen;
    }

    private static String normaliseEmail(String email, String externalId) {
        if (email == null || email.isBlank()) {
            // Clerk session tokens omit email unless custom claims are configured; the webhook fills it in later.
            return externalId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_") + PLACEHOLDER_EMAIL_DOMAIN;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > 100 ? t.substring(0, 100) : t;
    }

    // ------------------------------------------------------------------ profile & preferences

    public record ProfileDto(UUID id, String email, String firstName, String lastName, String imageUrl,
                             boolean emailPending, boolean onboardingCompleted, PreferencesDto preferences) {
    }

    public record PreferencesDto(DietaryPreference dietaryType, String currencyCode, String currencySymbol,
                                 int notifyExpiryDaysBefore, String notifyDailyDigestTime,
                                 boolean emailNotificationsEnabled, boolean pushNotificationsEnabled) {
    }

    public record UpdateProfileRequest(
            @jakarta.validation.constraints.Size(max = 100) String firstName,
            @jakarta.validation.constraints.Size(max = 100) String lastName) {
    }

    public record UpdatePreferencesRequest(
            DietaryPreference dietaryType,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(14) Integer notifyExpiryDaysBefore,
            @jakarta.validation.constraints.Pattern(regexp = "^([01]\\d|2[0-3]):(00|15|30|45)$",
                    message = "must be HH:mm on a 15-minute boundary, e.g. 08:15") String notifyDailyDigestTime,
            Boolean emailNotificationsEnabled,
            Boolean pushNotificationsEnabled) {
    }

    @Transactional(readOnly = true)
    public ProfileDto getProfile(UUID userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        return toProfile(user, getOrCreatePreferences(userId));
    }

    @Transactional
    public ProfileDto updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = userRepo.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        if (req.firstName() != null) {
            user.setFirstName(trim(req.firstName()));
        }
        if (req.lastName() != null) {
            user.setLastName(trim(req.lastName()));
        }
        return toProfile(userRepo.save(user), getOrCreatePreferences(userId));
    }

    @Transactional
    public ProfileDto completeOnboarding(UUID userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        if (user.getOnboardingCompletedAt() == null) {
            user.setOnboardingCompletedAt(Instant.now());
            user = userRepo.save(user);
        }
        return toProfile(user, getOrCreatePreferences(userId));
    }

    @Transactional
    public PreferencesDto updatePreferences(UUID userId, UpdatePreferencesRequest req) {
        UserPreferences prefs = getOrCreatePreferences(userId);
        if (req.dietaryType() != null) {
            prefs.setDietaryType(req.dietaryType());
        }
        if (req.notifyExpiryDaysBefore() != null) {
            prefs.setNotifyExpiryDaysBefore(req.notifyExpiryDaysBefore());
        }
        if (req.notifyDailyDigestTime() != null) {
            LocalTime t = LocalTime.parse(req.notifyDailyDigestTime());
            if (t.getMinute() % 15 != 0) {
                throw ApiException.validation("notifyDailyDigestTime", "Digest time must be on a 15-minute boundary.");
            }
            prefs.setNotifyDailyDigestTime(t);
        }
        if (req.emailNotificationsEnabled() != null) {
            prefs.setEmailNotificationsEnabled(req.emailNotificationsEnabled());
        }
        if (req.pushNotificationsEnabled() != null) {
            prefs.setPushNotificationsEnabled(req.pushNotificationsEnabled());
        }
        return toPrefs(prefsRepo.save(prefs));
    }

    @Transactional
    public UserPreferences getOrCreatePreferences(UUID userId) {
        return prefsRepo.findByUserId(userId).orElseGet(() -> {
            UserPreferences prefs = new UserPreferences();
            prefs.setUserId(userId);
            prefs.setDietaryType(DietaryPreference.OMNIVORE);
            prefs.setCurrencyCode("INR");
            prefs.setCurrencySymbol("₹");
            prefs.setNotifyExpiryDaysBefore(3);
            prefs.setNotifyDailyDigestTime(LocalTime.of(8, 0));
            prefs.setEmailNotificationsEnabled(true);
            prefs.setPushNotificationsEnabled(true);
            return prefsRepo.save(prefs);
        });
    }

    private ProfileDto toProfile(User u, UserPreferences p) {
        return new ProfileDto(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getImageUrl(),
                u.getEmail() != null && u.getEmail().endsWith(PLACEHOLDER_EMAIL_DOMAIN),
                u.getOnboardingCompletedAt() != null, toPrefs(p));
    }

    static PreferencesDto toPrefs(UserPreferences p) {
        return new PreferencesDto(p.getDietaryType(), p.getCurrencyCode(), p.getCurrencySymbol(), p.getNotifyExpiryDaysBefore(),
                p.getNotifyDailyDigestTime().toString(), p.isEmailNotificationsEnabled(), p.isPushNotificationsEnabled());
    }
}
