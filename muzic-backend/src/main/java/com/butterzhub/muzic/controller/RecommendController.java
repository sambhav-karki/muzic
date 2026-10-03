package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.dto.PromptRequest;
import com.butterzhub.muzic.dto.SongDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// @RestController makes this an HTTP controller whose return values become JSON bodies.
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RecommendController {

    // @PostMapping routes POST requests to /api/recommend to this method.
    @PostMapping("/recommend")
    public ResponseEntity<List<SongDto>> recommend(@RequestBody PromptRequest request) {

        String userPrompt = request.prompt();
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt must not be blank.");
        }

        // @RequestBody binds JSON to PromptRequest; this mock does not use the prompt yet.
        List<SongDto> songs = List.of(
                new SongDto(userPrompt + ": Midnight Study", "Lo-Fi Sample Artist", "mockLofi001",
                        "https://i.ytimg.com/vi/mockLofi001/hqdefault.jpg"),
                new SongDto(userPrompt + ": Acoustic Sunrise", "Acoustic Sample Artist", "mockAcou001",
                        "https://i.ytimg.com/vi/mockAcou001/hqdefault.jpg")
        );

        // ResponseEntity carries the HTTP status and body; ok(...) returns 200 OK.
        return ResponseEntity.ok(songs);
    }
}
