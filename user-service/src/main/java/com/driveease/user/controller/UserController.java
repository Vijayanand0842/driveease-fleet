package com.driveease.user.controller;

import com.driveease.common.security.JwtUserPrincipal;
import com.driveease.user.dto.UserResponse;
import com.driveease.user.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER')")
    public List<UserResponse> listAll() {
        return authService.listAll();
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id,
                                @AuthenticationPrincipal JwtUserPrincipal principal) {
        boolean privileged = principal.hasAnyRole("ADMIN", "FLEET_MANAGER");
        if (!privileged && !id.equals(principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view another user's profile");
        }
        return authService.getById(id);
    }
}
