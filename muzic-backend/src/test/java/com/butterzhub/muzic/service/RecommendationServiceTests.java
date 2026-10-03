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
        SongDto firstSong = new SongDto("Dreams", "Fleetwood Mac", "aaaaaaaaaaa", "https://i.ytimg.com/first.jpg");
        SongDto secondSong = new SongDto("Here Comes the Sun", "The Beatles", "bbbbbbbbbbb", "https://i.ytimg.com/second.jpg");
        when(llmClient.suggestSongs(prompt)).thenReturn(List.of(firstQuery, secondQuery));
        when(youTubeClient.searchSong(firstQuery)).thenReturn(firstSong);
        when(youTubeClient.searchSong(secondQuery)).thenReturn(secondSong);

        var result = service.getRecommendations(prompt);
        assertEquals(3, result.size());
        assertEquals(List.of(firstSong, secondSong), result.subList(0, 2));

        var order = inOrder(llmClient, youTubeClient);
        order.verify(llmClient).suggestSongs(prompt);
        order.verify(youTubeClient).searchSong(firstQuery);
        order.verify(youTubeClient).searchSong(secondQuery);

    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalid"})
    void skipsFailedLookupsWithoutLosingOtherSuggestions(String query) {
        SongDto song = new SongDto("Title", "Artist", "abcDEFG_123", "https://i.ytimg.com/cover.jpg");
        when(llmClient.suggestSongs("jazz")).thenReturn(List.of(query, "good"));
        when(youTubeClient.searchSong(query)).thenReturn(null);
        when(youTubeClient.searchSong("good")).thenReturn(song);
        var result = service.getRecommendations("jazz");
        assertEquals(3, result.size());
        assertEquals(song, result.get(0));
    }

    @Test
    void successfulModelQueriesKeepTheirResolvedSongs() {
        var queries = List.of("Metallica - Enter Sandman", "Nirvana - Lithium", "Queen - Somebody To Love");
        when(llmClient.suggestSongs("rock")).thenReturn(queries);
        var expected = new java.util.ArrayList<SongDto>();
        for (int i = 0; i < queries.size(); i++) {
            var song = new SongDto("Song " + i, "Artist " + i, String.valueOf(i).repeat(11), "https://i.ytimg.com/image.jpg");
            expected.add(song);
            when(youTubeClient.searchSong(queries.get(i))).thenReturn(song);
        }
        assertEquals(expected, service.getRecommendations("rock"));
    }

    @Test
    void totalOutagesRotateTheFinalBackupTracks() {
        when(llmClient.suggestSongs("rock")).thenReturn(List.of());
        var first = service.getRecommendations("rock");
        var second = service.getRecommendations("rock");
        assertEquals(3, first.size());
        assertEquals(3, second.size());
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
    }

    @Test
    void providerOutagesStillReturnThreeDistinctPopulatedTracks() {
        when(llmClient.suggestSongs("outage")).thenThrow(new IllegalStateException());
        when(youTubeClient.searchSong(org.mockito.ArgumentMatchers.anyString())).thenThrow(new IllegalStateException());
        var songs = service.getRecommendations("outage");
        assertEquals(3, songs.size());
        assertEquals(3, songs.stream().map(SongDto::youtubeVideoId).distinct().count());
        assertTrue(songs.stream().allMatch(s -> s.youtubeVideoId().matches("[A-Za-z0-9_-]{11}")
            && !s.title().isBlank() && !s.artist().isBlank() && s.thumbnailUrl().startsWith("https://")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankPromptsBeforeCallingClients(String prompt) {
        assertThrows(IllegalArgumentException.class, () -> service.getRecommendations(prompt));
        verifyNoInteractions(llmClient, youTubeClient);
    }
}
