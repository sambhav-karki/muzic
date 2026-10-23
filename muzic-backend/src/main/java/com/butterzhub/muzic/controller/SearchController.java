package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.dto.SongDto;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final YouTubeClient youtube;
    private final com.butterzhub.muzic.service.RecommendationService recommendations;
    public SearchController(YouTubeClient youtube, com.butterzhub.muzic.service.RecommendationService recommendations) {
        this.youtube = youtube; this.recommendations = recommendations;
    }

    @GetMapping({"", "/"})
    public YouTubeClient.SearchPage search(@RequestParam String q,
            @RequestParam(defaultValue = "NORMAL") String mode,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "9") int size,
            @RequestParam(required = false) String pageToken) {
        if ("AI".equalsIgnoreCase(mode)) {
            if (q.isBlank() || q.length() > 500 || page != 0 || size < 1 || size > 9)
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid AI search.");
            return new YouTubeClient.SearchPage(recommendations.getRecommendations(q).stream().limit(size).toList(), null, 0, 1);
        }
        if (!"NORMAL".equalsIgnoreCase(mode)) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Mode must be AI or NORMAL.");
        return youtube.searchPage(q, page, size, pageToken);
    }

    @GetMapping("/direct")
    public List<SongDto> direct(@RequestParam String query) {
        return youtube.searchMusic(query);
    }
}
