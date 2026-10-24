package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.dto.*;
import com.butterzhub.muzic.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import java.util.List;

@RestController
public class FeedController {
    private final YouTubeClient youtube;
    private final RecommendationService recommendations;
    private final DashboardCurationService dashboard;
    private final SwipeCurationService swipe;
    public FeedController(YouTubeClient youtube, RecommendationService recommendations, DashboardCurationService dashboard, SwipeCurationService swipe) {
        this.youtube = youtube; this.recommendations = recommendations; this.dashboard = dashboard; this.swipe = swipe;
    }
    @GetMapping("/api/curation/next-similar")
    public SongDto route(@RequestParam String videoId, @RequestParam(defaultValue = "") String title,
                         @RequestParam(defaultValue = "") String exclude, @RequestParam(required = false) String distance,
                         @RequestParam(required = false) String sessionId, OAuth2AuthenticationToken auth) {
        if (exclude.length() > 2400 || !videoId.matches("[A-Za-z0-9_-]{11}"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid track.");
        if (distance != null) return swipe.next(auth, sessionId, videoId, distance, new java.util.HashSet<>(List.of(exclude.split(","))));
        return next(videoId, title, exclude);
    }
    @GetMapping("/api/curation/swipe-session")
    public SwipeCurationService.Start start(OAuth2AuthenticationToken auth) { return swipe.start(auth); }

    public SongDto next(String videoId, String title,
                        @RequestParam(defaultValue = "") String exclude) {
        if (!videoId.matches("[A-Za-z0-9_-]{11}") || title.isBlank() || title.length() > 500 || exclude.length() > 2400)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid track.");
        var seen = new java.util.HashSet<>(List.of(exclude.split(","))); seen.add(videoId);
        var recommended = recommendations.getRecommendations("Find music with the same genre and mood as: " + title)
            .stream().filter(song -> !seen.contains(song.youtubeVideoId())).findFirst();
        if (recommended.isPresent()) return recommended.get();
        try {
            var related = youtube.searchPage(title + " similar music", 0, 9, null).tracks().stream()
                .filter(song -> !seen.contains(song.youtubeVideoId())).findFirst();
            if (related.isPresent()) return related.get();
        } catch (RuntimeException failure) { /* Keep the radio running during provider outages. */ }
        return com.butterzhub.muzic.client.FallbackTracks.forPrompt(title).stream()
            .filter(song -> !seen.contains(song.youtubeVideoId())).findFirst().orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No related track found. Retry or choose another vibe."));
    }
    @GetMapping("/api/user/feed")
    public DashboardCurationService.Feed feed(OAuth2AuthenticationToken auth) { return dashboard.feed(auth); }
}
