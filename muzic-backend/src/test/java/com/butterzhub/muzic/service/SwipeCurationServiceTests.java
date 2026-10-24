package com.butterzhub.muzic.service;
import com.butterzhub.muzic.client.YouTubeClient;
import com.butterzhub.muzic.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SwipeCurationServiceTests {
    private final PlaylistService playlists = mock(PlaylistService.class);
    private final YouTubeClient youtube = mock(YouTubeClient.class);
    private final SwipeCurationService service = new SwipeCurationService(playlists, youtube);
    private OAuth2AuthenticationToken auth(String name) {
        var auth = mock(OAuth2AuthenticationToken.class); when(auth.getName()).thenReturn(name); when(auth.getAuthorizedClientRegistrationId()).thenReturn("google"); return auth;
    }
    private SongDto song(int id, String artist) { return new SongDto("Song " + id, artist, String.format("%011d", id), null); }
    @Test void repeatedDislikesStayInsideTheOriginalAnchorArtistSpace() {
        var auth = auth("a");
        when(playlists.importYouTubePlaylists(auth)).thenReturn(List.of(new PlaylistDto("anchor", "90s Grunge", "", null, 50)));
        var tracks = java.util.stream.IntStream.range(0, 30).mapToObj(i -> song(i, List.of("Nirvana", "Pearl Jam", "Soundgarden").get(i % 3))).toList();
        when(playlists.importYouTubeTracks(auth, "anchor")).thenReturn(tracks);
        var start = service.start(auth); SongDto current = start.seed(); var seen = new HashSet<String>(); seen.add(current.youtubeVideoId());
        for (int i = 0; i < 12; i++) {
            SongDto next = service.next(auth, start.sessionId(), current.youtubeVideoId(), "divergent", seen);
            assertTrue(Set.of("Nirvana", "Pearl Jam", "Soundgarden").contains(next.artist()));
            assertNotEquals(current.artist(), next.artist()); assertTrue(seen.add(next.youtubeVideoId())); current = next;
        }
        verifyNoInteractions(youtube);
        assertThrows(ResponseStatusException.class, () -> service.next(auth("b"), start.sessionId(), start.seed().youtubeVideoId(), "close", Set.of()));
    }
    @Test void closeRejectsOutsideArtistsAndAlternateVersionsEvenWhenProviderReturnsThem() {
        var auth = auth("a");
        when(playlists.importYouTubePlaylists(auth)).thenReturn(List.of());
        var seed = new SongDto("Lithium", "Nirvana", "aaaaaaaaaaa", null);
        when(playlists.likedMusic(auth)).thenReturn(List.of(seed));
        when(youtube.searchPage(anyString(), eq(0), eq(9), isNull())).thenReturn(new YouTubeClient.SearchPage(List.of(
            new SongDto("Traditional Folk", "Unrelated Folk", "bbbbbbbbbbb", null),
            new SongDto("Lithium (Remastered)", "NirvanaVEVO", "ccccccccccc", null),
            new SongDto("Come As You Are", "NirvanaVEVO", "ddddddddddd", null)), "", 0, 1));
        var start = service.start(auth);
        assertEquals("Come As You Are", service.next(auth, start.sessionId(), seed.youtubeVideoId(), "close", Set.of()).title());
        assertThrows(ResponseStatusException.class, () -> service.next(auth, start.sessionId(), "zzzzzzzzzzz", "divergent", Set.of()));
        verify(youtube, never()).trendingMusic();
    }
    @Test void narrowGrungeDislikeCanStepToAFixedAdjacentBandButRejectsFolk() {
        var auth = auth("a");
        when(playlists.importYouTubePlaylists(auth)).thenReturn(List.of());
        var seed = new SongDto("Lithium", "Nirvana", "aaaaaaaaaaa", null);
        when(playlists.likedMusic(auth)).thenReturn(List.of(seed));
        when(youtube.searchPage(eq("Pearl Jam official audio"), eq(0), eq(9), isNull())).thenReturn(new YouTubeClient.SearchPage(List.of(
            new SongDto("Unrelated Folk", "Folk Band", "bbbbbbbbbbb", null), new SongDto("Alive", "Pearl Jam", "ccccccccccc", null)), "", 0, 1));
        var start = service.start(auth);
        assertEquals("Pearl Jam", service.next(auth, start.sessionId(), seed.youtubeVideoId(), "divergent", Set.of()).artist());
        verify(youtube, never()).trendingMusic();
    }
}
