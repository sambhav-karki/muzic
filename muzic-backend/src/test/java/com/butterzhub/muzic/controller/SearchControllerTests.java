package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.dto.SongDto;
import com.butterzhub.muzic.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SearchControllerTests {
    @Test void directSearchUsesOnlyYouTube() throws Exception {
        var youtube = mock(YouTubeClient.class);
        when(youtube.searchMusic("song")).thenReturn(java.util.List.of(
            new SongDto("Song", "Artist", "abcdefghijk", "https://example.com/image.jpg")));
        var mvc = MockMvcBuilders.standaloneSetup(new SearchController(youtube))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/search/direct").param("query", "song"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Song"));
        mvc.perform(get("/api/search/direct")).andExpect(status().isBadRequest());
        verify(youtube).searchMusic("song");
    }
}
