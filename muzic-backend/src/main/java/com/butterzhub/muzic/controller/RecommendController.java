package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.dto.RecommendRequest;
import com.butterzhub.muzic.dto.SongDto;
import com.butterzhub.muzic.service.RecommendationService;
import com.butterzhub.muzic.service.PlaylistService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:4200", "${frontend.url:http://localhost:4200}"})
public class RecommendController {

    private final RecommendationService recommendationService;
    private final PlaylistService playlists;

    public RecommendController(RecommendationService recommendationService, PlaylistService playlists) {
        this.recommendationService = recommendationService;
        this.playlists = playlists;
    }

    @PostMapping("/recommend")
    public ResponseEntity<List<SongDto>> recommend(@Valid @RequestBody RecommendRequest request,
                                                 OAuth2AuthenticationToken auth) {
        return ResponseEntity.ok(auth == null
            ? recommendationService.getRecommendations(request.prompt())
            : recommendationService.getRecommendations(request.prompt(), playlists.authenticatedUserId(auth)));
    }
}
