package com.ledgerline.profile;

import com.ledgerline.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ProfileDtos.ProfileResponse profile(@AuthenticationPrincipal AppUser user) {
        return profiles.profile(user);
    }

    @PutMapping
    public ProfileDtos.ProfileResponse updateProfile(@AuthenticationPrincipal AppUser user,
            @Valid @RequestBody ProfileDtos.UpdateProfileRequest request) {
        return profiles.updateProfile(user, request);
    }

    @GetMapping("/preferences")
    public ProfileDtos.PreferencesResponse preferences(@AuthenticationPrincipal AppUser user) {
        return profiles.preferences(user);
    }

    @PutMapping("/preferences")
    public ProfileDtos.PreferencesResponse updatePreferences(@AuthenticationPrincipal AppUser user,
            @Valid @RequestBody ProfileDtos.UpdatePreferencesRequest request) {
        return profiles.updatePreferences(user, request);
    }

    @PostMapping("/password")
    public void changePassword(@AuthenticationPrincipal AppUser user,
            @Valid @RequestBody ProfileDtos.ChangePasswordRequest request) {
        profiles.changePassword(user, request);
    }
}
