package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
public class ProfileController {
    private final UserRepository users;
    public ProfileController(UserRepository users) { this.users = users; }

    @GetMapping("/api/me")
    public Profile me(OAuth2AuthenticationToken auth) {
        if (auth == null) return new Profile(false, null, null, null);
        String subject = auth.getPrincipal().getAttribute("sub");
        var user = users.findByGoogleId(subject).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new Profile(true, user.getId(), user.getName(), user.getPictureUrl());
    }
    public record Profile(boolean authenticated, UUID id, String name, String pictureUrl) {}
}
