package com.carlos.backend_auth_tls.dto;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String username,
        List<String> roles) {
}
