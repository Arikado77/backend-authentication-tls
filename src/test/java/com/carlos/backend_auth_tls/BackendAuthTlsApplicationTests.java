package com.carlos.backend_auth_tls;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "app.security.jwt.issuer=backend-auth-tls-test",
        "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.security.jwt.expiration-seconds=900",
        "app.demo.user.password=test-user-password",
        "app.demo.admin.password=test-admin-password",
        "app.cors.allowed-origin=http://localhost:4200"
})
@AutoConfigureMockMvc
class BackendAuthTlsApplicationTests {
    private static final String USER_LOGIN = """
            {"username":"user","password":"test-user-password"}
            """;
    private static final String ADMIN_LOGIN = """
            {"username":"admin","password":"test-admin-password"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void publicInfoIsAvailableWithoutToken() throws Exception {
        mockMvc.perform(get("/api/public/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("backend-auth-tls"));
    }

    @Test
    void protectedProfileWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void userCanLoginAndReceiveJwt() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(USER_LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void incorrectCredentialsReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    @Test
    void validUserJwtCanReadProfile() throws Exception {
        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + loginToken(USER_LOGIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void missingJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtIsUnauthorized() throws Exception {
        String expiredToken = token("user", List.of("USER"), Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(60));
        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + loginToken(USER_LOGIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminCanAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + loginToken(ADMIN_LOGIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("ROLE_ADMIN"));
    }

    @Test
    void invalidJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void authorizationRulesCoverApiRoutesAndUnknownPaths() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/not-found"))
                .andExpect(status().isNotFound());
    }

    @Test
    void corsAllowsConfiguredOriginAndRejectsOtherOrigins() throws Exception {
        mockMvc.perform(options("/api/public/info")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mockMvc.perform(options("/api/public/info")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void missingLoginFieldsReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private String loginToken(String loginJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("accessToken").asString();
    }

    private String token(String subject, List<String> roles, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("backend-auth-tls-test")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
