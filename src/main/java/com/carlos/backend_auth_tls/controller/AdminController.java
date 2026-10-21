package com.carlos.backend_auth_tls.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carlos.backend_auth_tls.dto.DashboardResponse;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return new DashboardResponse("Panel administrativo disponible.", "ROLE_ADMIN");
    }
}
