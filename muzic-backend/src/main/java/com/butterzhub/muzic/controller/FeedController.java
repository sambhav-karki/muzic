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
    private final PlaylistService playlists;
    public FeedController(YouTubeClient youtube, RecommendationService recommendations, PlaylistService playlists) {
        this.youtube = youtube; this.recommendations = recommendations; this.playlists = playlists;
    }
    @GetMapping("/api/curation/next-similar")
    public SongDto next(@RequestParam String videoId, @RequestParam String title,
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
    public record Feed(List<SongDto> quickPicks, List<SongDto> trending, List<PlaylistDto> userPlaylists, String playlistError, String musicError) {}
    @GetMapping("/api/user/feed")
    public Feed feed(OAuth2AuthenticationToken auth) {
        List<SongDto> trending; String musicError = null;
        try { trending = youtube.trendingMusic(); }
        catch (RuntimeException failure) { trending = List.of(); musicError = "Charts are unavailable. Showing available music; retry to refresh."; }
        var chart = new java.util.LinkedHashMap<String, SongDto>();
        addTracks(chart, trending);
        if (chart.size() < 12) {
            try {
                addTracks(chart, youtube.searchPage("top music official audio", 0, 9, null).tracks());
                if (chart.size() < 12) addTracks(chart, youtube.searchPage("popular songs lyric video", 0, 9, null).tracks());
            } catch (RuntimeException failure) { musicError = "Some music is unavailable. Retry to refresh your shelves."; }
        }
        List<PlaylistDto> remote; String error = null;
        try { remote = playlists.importYouTubePlaylists(auth); }
        catch (RuntimeException failure) { remote = List.of(); error = "YouTube playlists unavailable. Reconnect or retry."; }
        var picks = new java.util.LinkedHashMap<String, SongDto>();
        try { addTracks(picks, playlists.likedMusic(auth)); }
        catch (RuntimeException failure) { musicError = "Liked music is unavailable. Showing chart picks; retry or reconnect Google."; }
        try { addTracks(picks, playlists.list(auth).stream().flatMap(playlist -> playlist.songs().stream()).toList()); }
        catch (RuntimeException failure) { /* Remote likes and charts can still populate the feed. */ }
        addTracks(picks, List.copyOf(chart.values()));
        addTracks(chart, List.copyOf(picks.values()));
        if (picks.isEmpty()) musicError = "Music could not be loaded from YouTube. Please retry.";
        return new Feed(picks.values().stream().limit(12).toList(), chart.values().stream().limit(15).toList(), remote, error, musicError);
    }
    private static void addTracks(java.util.Map<String, SongDto> target, List<SongDto> tracks) {
        for (SongDto track : tracks) {
            if (track != null && track.youtubeVideoId() != null && track.youtubeVideoId().matches("[A-Za-z0-9_-]{11}")
                && track.title() != null && !track.title().isBlank() && !"Private video".equals(track.title())
                && !"Deleted video".equals(track.title()) && track.artist() != null && !track.artist().isBlank())
                target.putIfAbsent(track.youtubeVideoId(), track);
        }
    }
}
