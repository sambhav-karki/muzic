package com.butterzhub.muzic.client;

import com.butterzhub.muzic.dto.SongDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class YouTubeClient {

    @Value("${youtube.api.key:}")
    private String apiKey;

    private final RestClient restClient;

    public YouTubeClient() {
        this(defaultBuilder());
    }

    private static RestClient.Builder defaultBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return RestClient.builder().requestFactory(factory);
    }

    YouTubeClient(RestClient.Builder builder) {
        this.restClient = builder.clone().baseUrl("https://www.googleapis.com/youtube/v3").build();
    }

    // Null means no verified match; RecommendationService omits that suggestion.
    public List<SongDto> trendingMusic() {
        if (apiKey == null || apiKey.isBlank()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Trending music requires YOUTUBE_API_KEY on the backend.");
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/videos?part=snippet,status&chart=mostPopular&videoCategoryId=10&regionCode=US&maxResults=20&key={key}", apiKey)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null || !(response.get("items") instanceof List<?> items)) throw new IllegalArgumentException();
            java.util.ArrayList<SongDto> songs = new java.util.ArrayList<>();
            for (Object value : items) {
                Map<?, ?> item = asMap(value), status = asMap(item.get("status")), snippet = asMap(item.get("snippet"));
                String id = text(item.get("id")), title = text(snippet.get("title")), artist = text(snippet.get("channelTitle"));
                if (!id.matches("[A-Za-z0-9_-]{11}") || !"public".equals(text(status.get("privacyStatus")))
                        || !"processed".equals(text(status.get("uploadStatus"))) || !Boolean.TRUE.equals(status.get("embeddable"))
                        || title.isBlank() || artist.isBlank() || title.length() > 255 || artist.length() > 255) continue;
                Map<?, ?> thumbnails = asMap(snippet.get("thumbnails"));
                String thumbnail = null;
                for (String size : List.of("high", "medium", "default")) {
                    String candidate = text(asMap(thumbnails.get(size)).get("url"));
                    if (validThumbnail(candidate)) { thumbnail = candidate; break; }
                }
                songs.add(new SongDto(title, artist, id, thumbnail));
            }
            return songs;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "YouTube trending music is unavailable. Check the API key and quota, then retry.");
        }
    }

    public SongDto searchSong(String query) {
        if (query == null || query.isBlank() || apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            // Embeddable, syndicated videos are suitable for the frontend player.
            Map<String, Object> response = restClient.get()
                    .uri("/search?part=snippet&maxResults=5&type=video&videoEmbeddable=true&videoSyndicated=true&q={query}&key={apiKey}",
                            query, apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null || !(response.get("items") instanceof List<?> items) || items.isEmpty()) {
                return null;
            }
            String ids = items.stream()
                    .map(value -> text(asMap(asMap(value).get("id")).get("videoId")))
                    .filter(id -> id.matches("[A-Za-z0-9_-]{11}"))
                    .distinct().collect(Collectors.joining(","));
            if (ids.isEmpty()) return null;
            // Search metadata can be stale: verify current existence and status.
            Map<String, Object> videos = restClient.get()
                    .uri("/videos?part=snippet,status&id={ids}&key={apiKey}", ids, apiKey)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (videos == null || !(videos.get("items") instanceof List<?> verified)) return null;
            for (String id : ids.split(",")) {
                for (Object value : verified) {
                    Map<?, ?> item = asMap(value);
                    if (!id.equals(text(item.get("id")))) continue;
                    Map<?, ?> status = asMap(item.get("status"));
                    if (!"public".equals(text(status.get("privacyStatus")))
                            || !"processed".equals(text(status.get("uploadStatus")))
                            || !Boolean.TRUE.equals(status.get("embeddable"))) continue;
                    Map<?, ?> snippet = asMap(item.get("snippet"));
                    String title = text(snippet.get("title"));
                    int separator = query.indexOf(" - ");
                    String artist = separator > 0 ? query.substring(0, separator).trim()
                            : text(snippet.get("channelTitle"));
                    Map<?, ?> thumbnails = asMap(snippet.get("thumbnails"));
                    String thumbnail = "";
                    for (String size : List.of("high", "medium", "default")) {
                        thumbnail = text(asMap(thumbnails.get(size)).get("url"));
                        if (validThumbnail(thumbnail)) {
                            break;
                        }
                    }
                    if (!validThumbnail(thumbnail) || title.isBlank() || title.length() > 255
                            || artist.isBlank() || artist.length() > 255) continue;
                    return new SongDto(title, artist, id, thumbnail);
                }
            }
        } catch (RestClientException | IllegalArgumentException exception) {
            // Skip unresolved suggestions rather than returning an unverified static ID.
        }
        return null;
    }

    private static boolean validThumbnail(String url) {
        try {
            URI uri = URI.create(url);
            return url.length() <= 2048 && "https".equals(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String text(Object value) {
        return value instanceof String string ? string : "";
    }
}
