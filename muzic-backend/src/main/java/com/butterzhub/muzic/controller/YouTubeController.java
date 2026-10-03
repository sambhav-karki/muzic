package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.dto.PlaylistDto;
import com.butterzhub.muzic.service.PlaylistService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/youtube")
public class YouTubeController {
    private final PlaylistService playlists;

    public YouTubeController(PlaylistService playlists) { this.playlists = playlists; }

    @GetMapping("/playlists")
    public ResponseEntity<PlaylistPage> playlists(OAuth2AuthenticationToken auth) {
        return ResponseEntity.ok(new PlaylistPage(playlists.importYouTubePlaylists(auth), null));
    }

    public record PlaylistPage(List<PlaylistDto> items, String nextPageToken) {}
}
