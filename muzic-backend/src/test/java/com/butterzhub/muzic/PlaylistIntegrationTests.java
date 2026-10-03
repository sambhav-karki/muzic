package com.butterzhub.muzic;

import com.butterzhub.muzic.client.YouTubeSyncClient;
import com.butterzhub.muzic.dto.PlaylistDto;
import com.butterzhub.muzic.dto.SavePlaylistRequest;
import com.butterzhub.muzic.model.User;
import com.butterzhub.muzic.repository.PlaylistRepository;
import com.butterzhub.muzic.repository.UserRepository;
import com.butterzhub.muzic.service.PlaylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlaylistIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private PlaylistService service;
    @Autowired private UserRepository users;
    @Autowired private PlaylistRepository playlists;
    @Autowired private SecurityFilterChain security;
    @MockitoBean private YouTubeSyncClient youtube;
    private OAuth2AuthenticationToken auth;
    private UUID userId;

    @BeforeEach
    void setup() {
        playlists.deleteAll();
        users.deleteAll();
        auth = identity("owner");
        User user = new User();
        user.setGoogleId("owner");
        user.setEmail("owner@example.com");
        userId = users.save(user).getId();
    }

    private OAuth2AuthenticationToken identity(String subject) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new OAuth2AuthenticationToken(new DefaultOAuth2User(authorities, Map.of("sub", subject), "sub"),
            authorities, "google");
    }

    private SavePlaylistRequest request() {
        return new SavePlaylistRequest("Evening jazz", List.of(
            new SavePlaylistRequest.Track("First", "Artist", "abcdefghijk", null),
            new SavePlaylistRequest.Track("Second", "Artist", "lmnopqrstuv", null)));
    }

    @Test
    void authStatusDistinguishesGuestsAndAuthenticatedSessions() throws Exception {
        mvc.perform(get("/api/auth/status")).andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(get("/api/auth/status").with(authentication(auth))).andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void corsAllowsCredentialedRequestsFromFrontendOrigins() throws Exception {
        for (String origin : List.of("https://butterzmuzic.vercel.app", "http://localhost:4200", "http://localhost:5173")) {
            for (String method : List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")) {
                mvc.perform(options("/api/auth/status")
                    .header("Origin", origin)
                    .header("Access-Control-Request-Method", method)
                    .header("Access-Control-Request-Headers", "Content-Type,X-Requested-With"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
            }
        }
        mvc.perform(options("/api/auth/status")
            .header("Origin", "https://untrusted.example")
            .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }

    @Test
    void profileDistinguishesGuestsAndAuthenticatedUsers() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").with(authentication(auth))).andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @Test
    void listsOnlyOwnedPlaylistsWithOrderedTracks() throws Exception {
        service.save(auth, request());
        User other = new User();
        other.setGoogleId("other");
        users.save(other);
        service.save(identity("other"), request());
        mvc.perform(get("/api/playlists/local")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/playlists/local").with(authentication(auth))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Evening jazz"))
            .andExpect(jsonPath("$[0].songs[0].title").value("First"))
            .andExpect(jsonPath("$[0].songs[1].youtubeVideoId").value("lmnopqrstuv"));
        verifyNoInteractions(youtube);
    }

    @Test
    void loginSuccessUpdatesExistingGoogleIdentity() throws Exception {
        var filter = security.getFilters().stream().filter(OAuth2LoginAuthenticationFilter.class::isInstance)
            .findFirst().orElseThrow();
        AuthenticationSuccessHandler handler = ReflectionTestUtils.invokeMethod(filter, "getSuccessHandler");
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var login = new OAuth2AuthenticationToken(new DefaultOAuth2User(authorities,
            Map.of("sub", "owner", "email", "updated@example.com", "name", "Updated Name",
                "picture", "https://example.com/avatar"), "sub"), authorities, "google");
        var response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, login);
        assertEquals("http://localhost:4200", response.getRedirectedUrl());
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), login);
        User updated = users.findByGoogleId("owner").orElseThrow();
        assertEquals(userId, updated.getId());
        assertEquals("updated@example.com", updated.getEmail());
        assertEquals("Updated Name", updated.getName());
        assertEquals(1, users.count());
    }

    @Test
    void anonymousProtectedRequestReturnsProblem() throws Exception {
        mvc.perform(get("/api/youtube/playlists")).andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.instance").value("/api/youtube/playlists"));
        verifyNoInteractions(youtube);
    }

    @Test
    void anonymousPlaylistPostStillRequiresLogin() throws Exception {
        mvc.perform(post("/api/playlists/local").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Jazz\",\"songs\":[]}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(youtube);
    }

    @Test
    void authenticatedExportDoesNotRequireCsrfToken() throws Exception {
        UUID id = service.save(auth, request()).id();
        when(youtube.createPlaylist(any(), anyString(), anyString())).thenReturn("remote");
        mvc.perform(post("/api/playlists/" + id + "/sync-youtube").with(authentication(auth)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.synced").value(true));
    }

    @Test
    void savesOwnedPlaylistAndRejectsInvalidMetadata() throws Exception {
        mvc.perform(post("/api/playlists/local").with(authentication(auth))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Jazz\",\"songs\":[{\"title\":\"First\",\"artist\":\"Artist\",\"youtubeVideoId\":\"abcdefghijk\"}]}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.itemCount").value(1));
        assertEquals(1, playlists.findByUserId(userId).size());
        assertTrue(service.historyContext(userId).contains("Artist - First"));
        mvc.perform(post("/api/playlists/local").with(authentication(auth))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"songs\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void cannotExportAnotherUsersPlaylist() throws Exception {
        UUID playlistId = service.save(auth, request()).id();
        User other = new User();
        other.setGoogleId("other");
        users.save(other);
        mvc.perform(post("/api/playlists/" + playlistId + "/sync-youtube")
            .with(authentication(identity("other"))))
            .andExpect(status().isNotFound());
        verifyNoInteractions(youtube);
    }

    @Test
    void failedExportRetainsProgressAndRetryResumes() {
        UUID id = service.save(auth, request()).id();
        when(youtube.createPlaylist(any(), anyString(), anyString())).thenReturn("remote");
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable"))
            .doNothing().when(youtube).addTrackToPlaylist(any(), eq("remote"), eq("lmnopqrstuv"));
        assertThrows(ResponseStatusException.class, () -> service.syncToYouTube(auth, id));
        var partial = playlists.findById(id).orElseThrow();
        assertEquals("remote", partial.getYoutubePlaylistId());
        assertEquals(1, partial.getSyncedTrackCount());
        assertTrue(service.syncToYouTube(auth, id).synced());
        assertTrue(service.syncToYouTube(auth, id).synced());
        verify(youtube, times(1)).createPlaylist(any(), anyString(), anyString());
        verify(youtube, times(1)).addTrackToPlaylist(any(), eq("remote"), eq("abcdefghijk"));
        assertEquals(2, playlists.findById(id).orElseThrow().getSyncedTrackCount());
    }

    @Test
    void managesYouTubePlaylistsAndValidatesRequests() throws Exception {
        var remote = new PlaylistDto("remote", "Soul", "Classic soul", null, 12);
        when(youtube.fetchUserPlaylists(any())).thenReturn(List.of(remote));
        when(youtube.createPlaylist(any(), eq("Soul"), eq(""), eq("private"))).thenReturn(remote);
        mvc.perform(get("/api/playlists").with(authentication(auth))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("remote")).andExpect(jsonPath("$[0].itemCount").value(12));
        mvc.perform(post("/api/playlists").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Soul\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Soul"));
        mvc.perform(post("/api/playlists/remote/items").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"videoId\":\"abcdefghijk\"}")).andExpect(status().isCreated());
        verify(youtube).insertTrack(any(), eq("remote"), eq("abcdefghijk"));
        mvc.perform(post("/api/playlists").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\" \",\"privacyStatus\":\"invalid\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/playlists/remote/items").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"videoId\":\"invalid\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/playlists")).andExpect(status().isUnauthorized());
    }

    @Test
    void importsPlaylistInterestsForCuration() throws Exception {
        when(youtube.fetchUserPlaylists(any())).thenReturn(List.of(new PlaylistDto("remote", "Soul", "Classic soul", null)));
        mvc.perform(get("/api/youtube/playlists").with(authentication(auth)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("Soul"));
        assertTrue(service.historyContext(userId).contains("Soul: Classic soul"));
    }
}
