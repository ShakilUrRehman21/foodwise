package com.foodwise.user;

import com.foodwise.common.ApiResponse;
import com.foodwise.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserService.ProfileDto> me(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(userService.getProfile(user.id()));
    }

    @PatchMapping("/me")
    public ApiResponse<UserService.ProfileDto> updateMe(@AuthenticationPrincipal CurrentUser user,
                                                        @Valid @RequestBody UserService.UpdateProfileRequest req) {
        return ApiResponse.ok(userService.updateProfile(user.id(), req), "Profile updated.");
    }

    @PostMapping("/me/onboarding-complete")
    public ApiResponse<UserService.ProfileDto> completeOnboarding(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(userService.completeOnboarding(user.id()));
    }

    @GetMapping("/preferences")
    public ApiResponse<UserService.PreferencesDto> preferences(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(UserService.toPrefs(userService.getOrCreatePreferences(user.id())));
    }

    @PatchMapping("/preferences")
    public ApiResponse<UserService.PreferencesDto> updatePreferences(@AuthenticationPrincipal CurrentUser user,
                                                                     @Valid @RequestBody UserService.UpdatePreferencesRequest req) {
        return ApiResponse.ok(userService.updatePreferences(user.id(), req), "Preferences saved.");
    }
}
