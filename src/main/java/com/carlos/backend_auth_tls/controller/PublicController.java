package com.carlos.backend_auth_tls.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carlos.backend_auth_tls.dto.InfoResponse;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    @GetMapping("/info")
    public InfoResponse info() {
        return new InfoResponse("backend-auth-tls", "API de autenticación y comunicaciones TLS.");
    }
}
