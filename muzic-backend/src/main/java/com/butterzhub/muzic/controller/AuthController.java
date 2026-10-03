package com.butterzhub.muzic.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    @GetMapping("/api/auth/status")
    public ResponseEntity<Map<String, Boolean>> getAuthStatus(Authentication authentication) {
        boolean authenticated = authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
        return ResponseEntity.ok(Map.of("authenticated", authenticated));
    }
}
