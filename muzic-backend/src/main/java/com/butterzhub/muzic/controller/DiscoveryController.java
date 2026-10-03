package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.dto.SongDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class DiscoveryController {
    private final YouTubeClient youtube;
    public DiscoveryController(YouTubeClient youtube) { this.youtube = youtube; }
    @GetMapping("/api/discovery/trending")
    public List<SongDto> trending() { return youtube.trendingMusic(); }
}
