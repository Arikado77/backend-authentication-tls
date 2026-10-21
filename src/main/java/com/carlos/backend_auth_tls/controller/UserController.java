package com.carlos.backend_auth_tls.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carlos.backend_auth_tls.dto.ProfileResponse;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/profile")
    public ProfileResponse profile(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .toList();
        return new ProfileResponse(authentication.getName(), roles);
    }
}
