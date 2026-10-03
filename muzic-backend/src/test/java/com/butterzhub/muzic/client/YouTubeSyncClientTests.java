package com.butterzhub.muzic.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class YouTubeSyncClientTests {
    private MockRestServiceServer server;
    private YouTubeSyncClient client;
    private OAuth2AuthorizedClientService clients;
    private OAuth2AuthenticationToken auth;
    private ClientRegistration registration;

    @BeforeEach
    void setup() {
        registration = ClientRegistration.withRegistrationId("google").clientId("test")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/callback").authorizationUri("https://example.com/auth")
            .tokenUri("https://example.com/token").build();
        clients = mock(OAuth2AuthorizedClientService.class);
        auth = new OAuth2AuthenticationToken(new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("sub", "google-sub"), "sub"),
            List.of(new SimpleGrantedAuthority("ROLE_USER")), "google");
        grant(Set.of("https://www.googleapis.com/auth/youtube"), Instant.now().plusSeconds(3600));
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.googleapis.com/youtube/v3");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YouTubeSyncClient(clients, new InMemoryClientRegistrationRepository(registration), builder.build());
    }

    private void grant(Set<String> scopes, Instant expires) {
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "delegated-token",
            Instant.now().minusSeconds(3600), expires, scopes);
        when(clients.loadAuthorizedClient("google", "google-sub"))
            .thenReturn(new OAuth2AuthorizedClient(registration, "google-sub", token));
    }

    @Test
    void createsPrivatePlaylistAndInsertsVideoWithDelegatedToken() {
        server.expect(requestTo("https://www.googleapis.com/youtube/v3/playlists?part=snippet,status"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer delegated-token"))
            .andExpect(jsonPath("$.snippet.title").value("Jazz"))
            .andExpect(jsonPath("$.status.privacyStatus").value("private"))
            .andRespond(withSuccess("{\"id\":\"remote-id\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer delegated-token"))
            .andExpect(jsonPath("$.snippet.playlistId").value("remote-id"))
            .andExpect(jsonPath("$.snippet.resourceId.kind").value("youtube#video"))
            .andExpect(jsonPath("$.snippet.resourceId.videoId").value("abcdefghijk"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertEquals("remote-id", client.createPlaylist(auth, "Jazz", "Evening"));
        client.addTrackToPlaylist(auth, "remote-id", "abcdefghijk");
        server.verify();
    }

    @Test
    void retriesPropagationFailuresBeforeInserting() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.CONFLICT)
            .body("{\"error\":{\"status\":\"ABORTED\"}}"));
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        client.addTrackToPlaylist(auth, "remote-id", "abcdefghijk");
        server.verify();
    }

    @Test
    void stopsAfterThreeRetries() {
        for (int attempt = 0; attempt < 4; attempt++) {
            server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
            () -> client.addTrackToPlaylist(auth, "remote-id", "abcdefghijk")).getStatusCode());
        server.verify();
    }

    @Test
    void skipsUnavailableVideosAndContinuesWithValidVideo() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND)
            .body("{\"error\":{\"errors\":[{\"reason\":\"videoNotFound\"}]}}"));
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN)
            .body("{\"error\":{\"errors\":[{\"reason\":\"countryRestriction\"}]}}"));
        server.expect(anything()).andExpect(jsonPath("$.snippet.resourceId.videoId").value("valid-video"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        client.addTrackToPlaylist(auth, "remote-id", "missing-video");
        client.addTrackToPlaylist(auth, "remote-id", "blocked-video");
        client.addTrackToPlaylist(auth, "remote-id", "valid-video");
        server.verify();
    }

    @Test
    void doesNotSkipMissingPlaylistOrQuotaFailure() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND)
            .body("{\"error\":{\"errors\":[{\"reason\":\"playlistNotFound\"}]}}"));
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN)
            .body("{\"error\":{\"errors\":[{\"reason\":\"quotaExceeded\"}]}}"));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
            () -> client.addTrackToPlaylist(auth, "remote-id", "abcdefghijk")).getStatusCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
            () -> client.addTrackToPlaylist(auth, "remote-id", "abcdefghijk")).getStatusCode());
        server.verify();
    }

    @Test
    void aggregatesAllPages() {
        server.expect(queryParam("mine", "true")).andExpect(header("Authorization", "Bearer delegated-token"))
            .andRespond(withSuccess("{\"items\":[{\"id\":\"one\",\"snippet\":{\"title\":\"Jazz\"}}],\"nextPageToken\":\"next\"}", MediaType.APPLICATION_JSON));
        server.expect(queryParam("pageToken", "next"))
            .andRespond(withSuccess("{\"items\":[{\"id\":\"two\",\"snippet\":{\"title\":\"Soul\"}}]}", MediaType.APPLICATION_JSON));
        assertEquals(List.of("Jazz", "Soul"), client.fetchUserPlaylists(auth).stream().map(p -> p.title()).toList());
        server.verify();
    }

    @Test
    void rejectsMissingScopeBeforeSendingRequest() {
        grant(Set.of("openid"), Instant.now().plusSeconds(3600));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> client.fetchUserPlaylists(auth)).getStatusCode());
        server.verify();
    }

    @Test
    void rejectsExpiredGrantWithoutRefreshToken() {
        grant(Set.of("https://www.googleapis.com/auth/youtube"), Instant.now().minusSeconds(60));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
            () -> client.fetchUserPlaylists(auth)).getStatusCode());
    }

    @Test
    void mapsQuotaFailure() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN)
            .body("{\"error\":{\"errors\":[{\"reason\":\"quotaExceeded\"}]}}"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
            () -> client.fetchUserPlaylists(auth)).getStatusCode());
        server.verify();
    }
}
