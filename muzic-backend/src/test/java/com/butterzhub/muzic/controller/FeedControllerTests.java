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
    private final FeedController controller = new FeedController(youtube, recommendations, playlists);
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
    @Test void emptyAccountUsesChartsAndPlaylistFailureDoesNotHideMusic() {
        when(youtube.trendingMusic()).thenReturn(List.of(next));
        when(playlists.list(null)).thenReturn(List.of());
        when(playlists.importYouTubePlaylists(null)).thenThrow(new RuntimeException("quota"));
        var feed = controller.feed(null);
        assertEquals(List.of(next), feed.quickPicks());
        assertEquals(List.of(next), feed.trending());
        assertTrue(feed.userPlaylists().isEmpty());
        assertNotNull(feed.playlistError());
    }
    @Test void savedMusicPopulatesQuickPicks() {
        when(youtube.trendingMusic()).thenReturn(List.of(next));
        when(playlists.list(null)).thenReturn(List.of(new PlaylistDetailDto(java.util.UUID.randomUUID(), "Favorites", null, List.of(current))));
        when(playlists.importYouTubePlaylists(null)).thenReturn(List.of());
        assertEquals(List.of(current, next), controller.feed(null).quickPicks());
    }
    @Test void realLikesLeadQuickPicksAndChartsFillTwelveUniqueSlots() {
        var chart = java.util.stream.IntStream.range(0, 15).mapToObj(index ->
            new SongDto("Chart " + index, "Artist", String.format("%011d", index), "https://example.com/art.jpg")).toList();
        when(youtube.trendingMusic()).thenReturn(chart);
        when(playlists.likedMusic(null)).thenReturn(List.of(current, chart.getFirst()));
        when(playlists.list(null)).thenReturn(List.of());
        var feed = controller.feed(null);
        assertEquals(12, feed.quickPicks().size());
        assertEquals(current, feed.quickPicks().getFirst());
        assertEquals(12, feed.quickPicks().stream().map(SongDto::youtubeVideoId).distinct().count());
        assertEquals(15, feed.trending().size());
        assertNull(feed.musicError());
    }
    @Test void providerOutageShowsAnErrorInsteadOfInventingChartItems() {
        when(youtube.trendingMusic()).thenThrow(new RuntimeException("quota"));
        when(youtube.searchPage(anyString(), eq(0), eq(9), isNull())).thenThrow(new RuntimeException("offline"));
        var feed = controller.feed(null);
        assertTrue(feed.quickPicks().isEmpty());
        assertTrue(feed.trending().isEmpty());
        assertNotNull(feed.musicError());
    }
}
