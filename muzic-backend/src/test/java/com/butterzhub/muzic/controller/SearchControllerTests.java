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
        var mvc = MockMvcBuilders.standaloneSetup(new SearchController(youtube, mock(com.butterzhub.muzic.service.RecommendationService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/search/direct").param("query", "song"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Song"));
        mvc.perform(get("/api/search/direct")).andExpect(status().isBadRequest());
        verify(youtube).searchMusic("song");
    }

    @Test void paginatedSearchPassesProviderTokenAndPage() throws Exception {
        var youtube = mock(YouTubeClient.class);
        var recommendations = mock(com.butterzhub.muzic.service.RecommendationService.class);
        var song = new SongDto("Song", "Artist", "abcdefghijk", "https://example.com/image.jpg");
        when(youtube.searchPage("jazz", 1, 9, "token-2")).thenReturn(new YouTubeClient.SearchPage(java.util.List.of(song), "token-3", 1, 4));
        when(recommendations.getRecommendations("night drive")).thenReturn(java.util.List.of(song));
        var mvc = MockMvcBuilders.standaloneSetup(new SearchController(youtube, recommendations)).build();
        mvc.perform(get("/api/search").param("q", "jazz").param("page", "1").param("pageToken", "token-2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nextPageToken").value("token-3"))
            .andExpect(jsonPath("$.tracks[0].title").value("Song")).andExpect(jsonPath("$.totalPages").value(4));
        mvc.perform(get("/api/search").param("q", "night drive").param("mode", "AI"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tracks[0].title").value("Song"));
        mvc.perform(get("/api/search").param("q", "jazz").param("mode", "invalid")).andExpect(status().isBadRequest());
    }
}
