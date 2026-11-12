package com.ledgerline.admin;

import com.ledgerline.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping
    public List<AdminUserDtos.UserResponse> list() {
        return users.list();
    }

    @PostMapping
    public AdminUserDtos.UserResponse create(@AuthenticationPrincipal AppUser actor,
            @Valid @RequestBody AdminUserDtos.CreateUserRequest request) {
        return users.create(actor, request);
    }

    @PatchMapping("/{userId}/role")
    public AdminUserDtos.UserResponse updateRole(@AuthenticationPrincipal AppUser actor,
            @PathVariable UUID userId, @Valid @RequestBody AdminUserDtos.UpdateRoleRequest request) {
        return users.updateRole(actor, userId, request);
    }
}
