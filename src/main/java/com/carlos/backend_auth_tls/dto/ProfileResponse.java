package com.carlos.backend_auth_tls.dto;

import java.util.List;

public record ProfileResponse(String username, List<String> roles) {
}
