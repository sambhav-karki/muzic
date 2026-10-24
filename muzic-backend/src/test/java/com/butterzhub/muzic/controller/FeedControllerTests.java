package com.butterzhub.muzic.controller;

import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.dto.*;
import com.butterzhub.muzic.service.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class FeedControllerTests {
    private final YouTubeClient youtube = mock(YouTubeClient.class);
    private final RecommendationService recommendations = mock(RecommendationService.class);
    private final PlaylistService playlists = mock(PlaylistService.class);
    private final FeedController controller = new FeedController(youtube, recommendations, new DashboardCurationService(youtube, playlists, mock(com.butterzhub.muzic.client.LlmClient.class)), mock(SwipeCurationService.class));
    private final SongDto current = new SongDto("Current", "Artist", "abcdefghijk", null);
    private final SongDto next = new SongDto("Next", "Artist", "lmnopqrstuv", null);

    @Test void continuationExcludesCurrentAndRecentlyPlayedTracks() {
        when(recommendations.getRecommendations(anyString())).thenReturn(List.of(current, next));
        assertEquals(next, controller.next(current.youtubeVideoId(), current.title(), ""));
        verifyNoInteractions(youtube);
    }
    @Test void outageStillContinuesWithAnUnheardStarterTrack() {
        when(recommendations.getRecommendations(anyString())).thenReturn(List.of(current));
        when(youtube.searchPage(anyString(), eq(0), eq(9), isNull())).thenThrow(new RuntimeException("offline"));
        assertNotEquals(current.youtubeVideoId(), controller.next(current.youtubeVideoId(), current.title(), "").youtubeVideoId());
    }
}