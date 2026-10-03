package com.butterzhub.muzic.client;

import com.butterzhub.muzic.dto.SongDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class YouTubeClient {
    private static final SongDto FALLBACK = new SongDto(
            "Never Gonna Give You Up", "Rick Astley", "dQw4w9WgXcQ",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");

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

    public SongDto searchSong(String query) {
        if (query == null || query.isBlank() || apiKey == null || apiKey.isBlank()) {
            return FALLBACK;
        }
        try {
            // Embeddable, syndicated videos are suitable for the frontend player.
            Map<String, Object> response = restClient.get()
                    .uri("/search?part=snippet&maxResults=1&type=video&videoEmbeddable=true&videoSyndicated=true&q={query}&key={apiKey}",
                            query, apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null || !(response.get("items") instanceof List<?> items) || items.isEmpty()) {
                return FALLBACK;
            }
            Map<?, ?> item = asMap(items.getFirst());
            Map<?, ?> snippet = asMap(item.get("snippet"));
            String id = text(asMap(item.get("id")).get("videoId"));
            String title = text(snippet.get("title"));
            if (!id.matches("[A-Za-z0-9_-]{11}") || title.isBlank()) {
                return FALLBACK;
            }
            int separator = query.indexOf(" - ");
            String artist = separator > 0 ? query.substring(0, separator).trim()
                    : text(snippet.get("channelTitle"));
            Map<?, ?> thumbnails = asMap(snippet.get("thumbnails"));
            String thumbnail = "";
            for (String size : List.of("high", "medium", "default")) {
                thumbnail = text(asMap(thumbnails.get(size)).get("url"));
                if (!thumbnail.isBlank()) {
                    break;
                }
            }
            if (thumbnail.isBlank()) {
                thumbnail = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
            }
            return new SongDto(title, artist, id, thumbnail);
        } catch (RestClientException | IllegalArgumentException exception) {
            // Quota, transport, and malformed responses use accurate static metadata.
            return FALLBACK;
        }
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String text(Object value) {
        return value instanceof String string ? string : "";
    }
}
