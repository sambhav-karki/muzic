package com.butterzhub.muzic.service;

import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.dto.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DashboardCurationServiceTests {
    private final YouTubeClient youtube = mock(YouTubeClient.class);
    private final PlaylistService playlists = mock(PlaylistService.class);
    private final com.butterzhub.muzic.client.LlmClient llm = mock(com.butterzhub.muzic.client.LlmClient.class);
    private final DashboardCurationService service = new DashboardCurationService(youtube, playlists, llm);
    private static SongDto song(int id) { return new SongDto("Song " + id, "Artist " + (id % 8), String.format("%011d", id), null); }
    private static List<SongDto> tracks(int first, int count) { return java.util.stream.IntStream.range(first, first + count).mapToObj(DashboardCurationServiceTests::song).toList(); }
    private void global() { when(youtube.searchPage("Top Global Music Hits", 0, 9, null)).thenReturn(new YouTubeClient.SearchPage(tracks(100, 9), "", 0, 1)); }

    @Test void largestPlaylistAnchorsExactlyThreeFamiliarAndSixMatchingDiscoveries() {
        global();
        when(playlists.importYouTubePlaylists(null)).thenReturn(List.of(
            new PlaylistDto("small", "Small", "", null, 3), new PlaylistDto("large", "My Jazz", "", null, 100)));
        var anchor = tracks(0, 12);
        when(playlists.importYouTubeTracks(null, "large")).thenReturn(anchor);
        when(youtube.searchPage(startsWith("Artist "), eq(0), eq(9), isNull()))
            .thenReturn(new YouTubeClient.SearchPage(tracks(20, 9), "", 0, 1));
        when(playlists.likedMusic(null)).thenReturn(List.of(song(20), song(70), song(71)));
        var feed = service.feed(null);
        assertEquals("My Jazz", feed.primaryAnchor());
        assertEquals(9, feed.quickPicks().size());
        assertEquals(3, feed.quickPicks().stream().filter(anchor::contains).count());
        assertEquals(6, feed.quickPicks().stream().filter(tracks(20, 9)::contains).count());
        assertEquals(List.of(song(71), song(70)), feed.forgottenFavorites());
        verify(playlists, never()).importYouTubeTracks(null, "small");
        verify(youtube, never()).trendingMusic();
    }
    @Test void noPlaylistsUsesExplicitGlobalMusicSearchWithoutRegionalCharts() {
        global(); when(playlists.importYouTubePlaylists(null)).thenReturn(List.of());
        var feed = service.feed(null);
        assertEquals(9, feed.quickPicks().size());
        assertEquals(feed.globalHits(), feed.quickPicks());
        assertEquals("Global Music", feed.primaryAnchor());
        verify(youtube, never()).trendingMusic();
    }
    @Test void sparseMatchingDataDoesNotFillPersonalMixWithUnrelatedCharts() {
        global();
        when(playlists.importYouTubePlaylists(null)).thenReturn(List.of(new PlaylistDto("large", "Jazz", "", null, 10)));
        when(playlists.importYouTubeTracks(null, "large")).thenReturn(tracks(0, 3));
        when(youtube.searchPage(startsWith("Artist "), eq(0), eq(9), isNull()))
            .thenReturn(new YouTubeClient.SearchPage(tracks(0, 3), "", 0, 1));
        var feed = service.feed(null);
        assertEquals(3, feed.quickPicks().size());
        assertTrue(feed.quickPicks().stream().noneMatch(tracks(100, 9)::contains));
        assertNotNull(feed.musicError());
    }
    @Test void playlistOutageIsNotTreatedAsANewAccount() {
        global(); when(playlists.importYouTubePlaylists(null)).thenThrow(new RuntimeException("offline"));
        var feed = service.feed(null);
        assertTrue(feed.quickPicks().isEmpty());
        assertNotNull(feed.playlistError());
        assertEquals(9, feed.globalHits().size());
    }
    @Test void forgottenFavoritesExcludePreviousQuickPicksForTheSameUser() {
        global();
        var auth = mock(org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken.class);
        when(auth.getName()).thenReturn("user-a");
        when(auth.getAuthorizedClientRegistrationId()).thenReturn("google");
        when(playlists.importYouTubePlaylists(auth)).thenReturn(List.of(new PlaylistDto("large", "Jazz", "", null, 10)));
        when(playlists.importYouTubeTracks(auth, "large")).thenReturn(tracks(0, 3));
        when(playlists.likedMusic(auth)).thenReturn(List.of(song(20), song(80)));
        when(youtube.searchPage(startsWith("Artist "), eq(0), eq(9), isNull()))
            .thenReturn(new YouTubeClient.SearchPage(tracks(20, 9), "", 0, 1))
            .thenReturn(new YouTubeClient.SearchPage(tracks(30, 9), "", 0, 1));
        service.feed(auth);
        assertEquals(List.of(song(80)), service.feed(auth).forgottenFavorites());
    }
    @Test void narrowLibraryExpandsToRelatedArtistsInsteadOfRepeatingLithiumVersions() {
        global();
        when(playlists.importYouTubePlaylists(null)).thenReturn(List.of(new PlaylistDto("grunge", "Grunge", "", null, 20)));
        var versions = java.util.stream.IntStream.range(0, 5).mapToObj(i -> new SongDto(i == 0 ? "Lithium" : "Lithium (Remastered " + i + ")", "Nirvana", String.format("%011d", i), null)).toList();
        var library = new ArrayList<>(versions); library.add(new SongDto("In Bloom", "NirvanaVEVO", "aaaaaaaaaaa", null));
        when(playlists.importYouTubeTracks(null, "grunge")).thenReturn(library);
        when(youtube.searchPage(startsWith("Nirvana"), eq(0), eq(9), isNull())).thenReturn(new YouTubeClient.SearchPage(versions, "", 0, 1));
        when(llm.suggestTasteSongs(anyString())).thenReturn(List.of("Pearl Jam - Alive", "Soundgarden - Black Hole Sun", "Alice in Chains - Rooster"))
            .thenReturn(List.of("Foo Fighters - Everlong"));
        int id = 30;
        for (String artist : List.of("Pearl Jam", "Soundgarden", "Alice in Chains", "Foo Fighters")) {
            var tracks = List.of(new SongDto("Distinct " + id, artist, String.format("%011d", id++), null), new SongDto("Distinct " + id, artist, String.format("%011d", id++), null));
            when(youtube.searchPage(startsWith(artist + " - "), eq(0), eq(9), isNull())).thenReturn(new YouTubeClient.SearchPage(tracks, "", 0, 1));
        }
        var picks = service.feed(null).quickPicks();
        assertEquals(9, picks.size());
        assertEquals(9, picks.stream().map(song -> TrackIdentity.title(song.title())).distinct().count());
        assertEquals(2, picks.stream().filter(song -> TrackIdentity.artist(song.artist()).equals("nirvana")).count());
        assertEquals(1, picks.stream().filter(song -> TrackIdentity.title(song.title()).equals("lithium")).count());
    }
}
