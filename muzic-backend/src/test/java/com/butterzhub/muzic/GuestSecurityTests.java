package com.butterzhub.muzic;

import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GuestSecurityTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private YouTubeClient youtube;
    @MockitoBean private RecommendationService recommendations;

    @Test void guestsCanSearchAndDiscover() throws Exception {
        when(youtube.searchMusic("jazz")).thenReturn(List.of());
        when(youtube.trendingMusic()).thenReturn(List.of());
        when(recommendations.getRecommendations("jazz")).thenReturn(List.of());
        mvc.perform(get("/api/auth/status")).andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(get("/api/search/direct").param("query", "jazz")).andExpect(status().isOk());
        when(youtube.searchPage("jazz", 0, 9, null)).thenReturn(new YouTubeClient.SearchPage(List.of(), null, 0, 1));
        mvc.perform(get("/api/search").param("q", "jazz")).andExpect(status().isOk());
        mvc.perform(get("/api/user/feed")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/curation/swipe-session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/curation/next-similar").param("videoId", "abcdefghijk").param("distance", "close"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/discovery/trending")).andExpect(status().isOk());
        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON)
            .content("{\"prompt\":\"jazz\"}")).andExpect(status().isOk());
        verify(recommendations).getRecommendations("jazz");
    }

    @Test void guestsCannotReadCreateOrSyncPlaylists() throws Exception {
        for (String path : List.of("/api/playlists", "/api/playlists/local", "/api/playlists/example/items", "/api/youtube/playlists")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        for (String path : List.of("/api/playlists", "/api/playlists/local", "/api/playlists/example/items", "/api/playlists/example/sync-youtube")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        }
    }
}
