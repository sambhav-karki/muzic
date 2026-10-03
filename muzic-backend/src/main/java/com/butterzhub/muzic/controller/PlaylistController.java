package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.dto.SavePlaylistRequest;
import com.butterzhub.muzic.dto.SavedPlaylistDto;
import com.butterzhub.muzic.dto.PlaylistDetailDto;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import com.butterzhub.muzic.service.PlaylistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

// @RestController serializes transport records; services own persistence and provider calls.
@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {
    private final PlaylistService playlists;

    public PlaylistController(PlaylistService playlists) { this.playlists = playlists; }

    @GetMapping({"", "/"})
    public List<PlaylistDetailDto> list(OAuth2AuthenticationToken auth) {
        return playlists.list(auth);
    }

    @PostMapping({"", "/"})
    public ResponseEntity<SavedPlaylistDto> save(@Valid @RequestBody SavePlaylistRequest request,
                                              OAuth2AuthenticationToken auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(playlists.save(auth, request));
    }

    @PostMapping("/{id}/sync-youtube")
    public ResponseEntity<PlaylistService.SyncResult> sync(@PathVariable UUID id, OAuth2AuthenticationToken auth) {
        return ResponseEntity.ok(playlists.syncToYouTube(auth, id));
    }
}
