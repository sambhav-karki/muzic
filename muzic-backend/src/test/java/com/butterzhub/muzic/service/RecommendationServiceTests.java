package com.butterzhub.muzic.service;

import com.butterzhub.muzic.client.LlmClient;
import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.dto.SongDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecommendationServiceTests {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final YouTubeClient youTubeClient = mock(YouTubeClient.class);
    private final PlaylistService playlists = mock(PlaylistService.class);
    private final RecommendationService service = new RecommendationService(youTubeClient, llmClient, playlists);

    @Test
    void personalizesUsingSavedHistory() {
        UUID userId = UUID.randomUUID();
        when(playlists.historyContext(userId)).thenReturn("Nina Simone - Feeling Good");
        when(llmClient.suggestSongs(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        service.getRecommendations("evening jazz", userId);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).suggestSongs(prompt.capture());
        assertTrue(prompt.getValue().startsWith("evening jazz"));
        assertTrue(prompt.getValue().contains("Nina Simone - Feeling Good"));
        verify(playlists).historyContext(userId);
    }

    @Test
    void resolvesSuggestionsInTheirOriginalOrder() {
        String prompt = "Classic songs for a road trip";
        String firstQuery = "Fleetwood Mac - Dreams";
        String secondQuery = "The Beatles - Here Comes the Sun";
        SongDto firstSong = new SongDto("Dreams", "Fleetwood Mac", "firstVideo", "firstThumbnail");
        SongDto secondSong = new SongDto("Here Comes the Sun", "The Beatles", "secondVideo", "secondThumbnail");
        when(llmClient.suggestSongs(prompt)).thenReturn(List.of(firstQuery, secondQuery));
        when(youTubeClient.searchSong(firstQuery)).thenReturn(firstSong);
        when(youTubeClient.searchSong(secondQuery)).thenReturn(secondSong);

        assertEquals(List.of(firstSong, secondSong), service.getRecommendations(prompt));

        var order = inOrder(llmClient, youTubeClient);
        order.verify(llmClient).suggestSongs(prompt);
        order.verify(youTubeClient).searchSong(firstQuery);
        order.verify(youTubeClient).searchSong(secondQuery);
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankPromptsBeforeCallingClients(String prompt) {
        assertThrows(IllegalArgumentException.class, () -> service.getRecommendations(prompt));
        verifyNoInteractions(llmClient, youTubeClient);
    }
}
