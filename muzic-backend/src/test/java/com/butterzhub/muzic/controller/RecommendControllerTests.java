package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.dto.SongDto;
import com.butterzhub.muzic.exception.GlobalExceptionHandler;
import com.butterzhub.muzic.service.RecommendationService;
import com.butterzhub.muzic.service.PlaylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecommendControllerTests {
    private RecommendationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(RecommendationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RecommendController(service, mock(PlaylistService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsResolvedTracks() throws Exception {
        when(service.getRecommendations("jazz"))
                .thenReturn(List.of(new SongDto("Title", "Artist", "video123", "https://example.com/thumb.jpg")));

        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"jazz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Title"))
                .andExpect(jsonPath("$[0].artist").value("Artist"))
                .andExpect(jsonPath("$[0].youtubeVideoId").value("video123"))
                .andExpect(jsonPath("$[0].thumbnailUrl").value("https://example.com/thumb.jpg"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"prompt\":null}", "{\"prompt\":\"\"}", "{\"prompt\":\"   \"}"})
    void rejectsInvalidPromptBeforeCallingService(String body) throws Exception {
        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Prompt must not be blank."));
        verifyNoInteractions(service);
    }

    @Test
    void mapsServiceArgumentFailureToProblemDetail() throws Exception {
        when(service.getRecommendations("jazz")).thenThrow(new IllegalArgumentException("Invalid prompt."));
        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"jazz\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid prompt."));
    }

    @Test
    void permitsAngularOrigin() throws Exception {
        when(service.getRecommendations("jazz")).thenReturn(List.of());
        mvc.perform(post("/api/recommend").header("Origin", "http://localhost:4200")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"jazz\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        mvc.perform(post("/api/recommend").header("Origin", "https://example.com")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"jazz\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(service);
    }
}
