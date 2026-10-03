package com.butterzhub.muzic.client;

import com.butterzhub.muzic.dto.PlaylistDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClientException;

@Component
public class YouTubeSyncClient {
    private static final Logger logger = LoggerFactory.getLogger(YouTubeSyncClient.class);
    private static final ObjectMapper errors = new ObjectMapper();
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube";
    private final OAuth2AuthorizedClientService clients;
    private final AuthorizedClientServiceOAuth2AuthorizedClientManager manager;
    private final RestClient rest;

    @Autowired
    public YouTubeSyncClient(OAuth2AuthorizedClientService clients, ClientRegistrationRepository registrations,
                             RestClient.Builder builder) {
        this(clients, registrations, transport(builder));
    }

    YouTubeSyncClient(OAuth2AuthorizedClientService clients, ClientRegistrationRepository registrations,
                      RestClient transport) {
        this.clients = clients;
        manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().refreshToken().build());
        rest = transport;
    }

    private static RestClient transport(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return builder.clone().baseUrl("https://www.googleapis.com/youtube/v3").requestFactory(factory).build();
    }

    private String token(OAuth2AuthenticationToken auth) {
        var client = clients.loadAuthorizedClient(auth.getAuthorizedClientRegistrationId(), auth.getName());
        if (client == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google login required");
        try {
            client = manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(
                auth.getAuthorizedClientRegistrationId()).principal(auth).build());
        } catch (org.springframework.security.oauth2.core.OAuth2AuthorizationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google authorization must be renewed");
        }
        if (client == null || client.getAccessToken().getExpiresAt() == null
            || !client.getAccessToken().getExpiresAt().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google authorization has expired");
        if (!client.getAccessToken().getScopes().contains(SCOPE))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "YouTube permission required");
        return client.getAccessToken().getTokenValue();
    }

    public String createPlaylist(OAuth2AuthenticationToken auth, String title, String description) {
        return createPlaylist(auth, title, description, "private").id();
    }
    public PlaylistDto createPlaylist(OAuth2AuthenticationToken auth, String title, String description, String privacy) {
        String bearer = token(auth);
        JsonNode result = call(() -> rest.post().uri("/playlists?part=snippet,status")
            .headers(h -> h.setBearerAuth(bearer))
            .body(Map.of("snippet", Map.of("title", title, "description", description), "status", Map.of("privacyStatus", privacy)))
            .retrieve().body(JsonNode.class));
        if (result == null || result.path("id").asText().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Missing YouTube playlist ID");
        return playlist(result);
    }
    // Interactive writes surface unavailable videos rather than silently skipping them.
    public void insertTrack(OAuth2AuthenticationToken auth, String playlistId, String videoId) {
        String bearer = token(auth);
        call(() -> rest.post().uri("/playlistItems?part=snippet")
            .headers(h -> h.setBearerAuth(bearer))
            .body(Map.of("snippet", Map.of("playlistId", playlistId,
                "resourceId", Map.of("kind", "youtube#video", "videoId", videoId))))
            .retrieve().toBodilessEntity());
    }
    private PlaylistDto playlist(JsonNode item) {
        JsonNode snippet = item.path("snippet");
        return new PlaylistDto(item.path("id").asText(), snippet.path("title").asText(), snippet.path("description").asText(),
            snippet.path("thumbnails").path("default").path("url").asText(null), item.path("contentDetails").path("itemCount").asInt());
    }

    public void addTrackToPlaylist(OAuth2AuthenticationToken auth, String playlistId, String videoId) {
        String bearer = token(auth);
        call(() -> {
            for (int attempt = 0; ; attempt++) {
                try {
                    return rest.post().uri("/playlistItems?part=snippet")
                        .headers(h -> h.setBearerAuth(bearer))
                        .body(Map.of("snippet", Map.of("playlistId", playlistId,
                            "resourceId", Map.of("kind", "youtube#video", "videoId", videoId))))
                        .retrieve().toBodilessEntity();
                } catch (RestClientResponseException exception) {
                    int status = exception.getStatusCode().value();
                    if (unavailableVideo(exception)) {
                        logger.warn("Skipping unavailable YouTube video {} in playlist {}: HTTP {}; response body: {}",
                            videoId, playlistId, status, exception.getResponseBodyAsString());
                        return null;
                    }
                    if ((status != 409 && status != 503) || attempt >= 3) throw exception;
                    long delay = 500L << attempt;
                    logger.warn("Retrying YouTube video {} in playlist {} in {} ms: HTTP {}; response body: {}",
                        videoId, playlistId, delay, status, exception.getResponseBodyAsString());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "YouTube sync interrupted; retry to resume", interrupted);
                    }
                }
            }
        });
    }

    private boolean unavailableVideo(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status != 404 && status != 403 && status != 400) return false;
        try {
            JsonNode body = errors.readTree(exception.getResponseBodyAsString());
            if (body == null) return false;
            JsonNode error = body.path("error");
            for (JsonNode detail : error.path("errors")) {
                String reason = detail.path("reason").asText();
                if (status == 404 && reason.equals("videoNotFound")) return true;
                if (reason.equals("countryRestriction") || reason.equals("regionRestriction")
                    || reason.equals("geoRestriction") || reason.equals("videoNotAvailable")) return true;
            }
            return status == 404 && error.path("message").asText().equalsIgnoreCase("Video not found");
        } catch (JsonProcessingException invalid) {
            return false;
        }
    }

    public List<PlaylistDto> fetchUserPlaylists(OAuth2AuthenticationToken auth) {
        String bearer = token(auth);
        List<PlaylistDto> items = new ArrayList<>();
        var seen = new HashSet<String>();
        String page = "";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        do {
            if (System.nanoTime() >= deadline)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "YouTube import time budget reached");
            String currentPage = page;
            JsonNode body = call(() -> rest.get().uri(uri -> uri.path("/playlists")
                .queryParam("part", "snippet,contentDetails").queryParam("mine", true).queryParam("maxResults", 50)
                .queryParam("pageToken", currentPage).build())
                .headers(h -> h.setBearerAuth(bearer)).retrieve().body(JsonNode.class));
            if (body == null || !body.path("items").isArray())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid YouTube playlist response");
            for (JsonNode item : body.path("items")) {
                items.add(playlist(item));
            }
            page = body.path("nextPageToken").asText("");
            if (!page.isEmpty() && !seen.add(page))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid YouTube pagination");
        } while (!page.isEmpty());
        return List.copyOf(items);
    }

    public List<com.butterzhub.muzic.dto.SongDto> fetchPlaylistItems(OAuth2AuthenticationToken auth, String playlistId) {
        if (playlistId == null || !playlistId.matches("[A-Za-z0-9_-]{1,150}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid playlist ID");
        String bearer = token(auth);
        var songs = new ArrayList<com.butterzhub.muzic.dto.SongDto>();
        var seenPages = new HashSet<String>();
        var seenVideos = new HashSet<String>();
        String page = "";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        do {
            if (System.nanoTime() >= deadline)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "YouTube import time budget reached; retry");
            String currentPage = page;
            JsonNode body = call(() -> rest.get().uri(uri -> uri.path("/playlistItems")
                .queryParam("part", "snippet,contentDetails").queryParam("playlistId", playlistId)
                .queryParam("maxResults", 50).queryParam("pageToken", currentPage).build())
                .headers(h -> h.setBearerAuth(bearer)).retrieve().body(JsonNode.class));
            if (body == null || !body.path("items").isArray())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid YouTube tracks response");
            for (JsonNode item : body.path("items")) {
                JsonNode snippet = item.path("snippet");
                String id = item.path("contentDetails").path("videoId").asText(snippet.path("resourceId").path("videoId").asText());
                String title = snippet.path("title").asText();
                if (!id.matches("[A-Za-z0-9_-]{11}") || title.isBlank() || title.equals("Private video")
                    || title.equals("Deleted video") || !seenVideos.add(id)) continue;
                String artist = snippet.path("videoOwnerChannelTitle").asText(snippet.path("channelTitle").asText("YouTube"));
                if (artist.isBlank()) artist = "YouTube";
                songs.add(new com.butterzhub.muzic.dto.SongDto(title, artist, id,
                    "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg"));
            }
            page = body.path("nextPageToken").asText("");
            if (!page.isEmpty() && !seenPages.add(page))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid YouTube pagination");
        } while (!page.isEmpty());
        return List.copyOf(songs);
    }

    private <T> T call(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            logger.error("YouTube API request failed: HTTP {}; response body: {}",
                status, exception.getResponseBodyAsString());
            HttpStatus mapped = status == 401 ? HttpStatus.UNAUTHORIZED
                : status == 403 ? (exception.getResponseBodyAsString().contains("quotaExceeded")
                    ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.FORBIDDEN)
                : status == 429 ? HttpStatus.TOO_MANY_REQUESTS
                : status >= 500 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(mapped, "YouTube request failed");
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "YouTube is unavailable");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid YouTube response");
        }
    }
}
