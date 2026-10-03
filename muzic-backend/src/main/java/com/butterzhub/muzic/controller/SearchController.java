package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.dto.SongDto;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final YouTubeClient youtube;
    public SearchController(YouTubeClient youtube) { this.youtube = youtube; }

    @GetMapping("/direct")
    public List<SongDto> direct(@RequestParam String query) {
        return youtube.searchMusic(query);
    }
}
