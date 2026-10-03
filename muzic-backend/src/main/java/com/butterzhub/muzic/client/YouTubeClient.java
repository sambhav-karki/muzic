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
    private final java.util.concurrent.ConcurrentHashMap<String, SongDto> songCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, List<SongDto>> webCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long quotaRetryAfter;
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private boolean useWebSearch() {
        return apiKey == null || apiKey.isBlank() || System.currentTimeMillis() < quotaRetryAfter;
    }
    private void rememberQuotaFailure(RestClientException failure) {
        if (failure instanceof org.springframework.web.client.RestClientResponseException response
            && response.getStatusCode().value() == 403 && (response.getResponseBodyAsString().contains("quotaExceeded")
                || response.getResponseBodyAsString().contains("dailyLimitExceeded")))
            quotaRetryAfter = System.currentTimeMillis() + java.util.concurrent.TimeUnit.MINUTES.toMillis(30);
    }
    private static String cacheKey(String query) { return query.trim().toLowerCase(java.util.Locale.ROOT); }


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

    // Trending still uses videos.list; search can fall back to public results.
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

    public List<SongDto> searchMusic(String query) {
        if (query == null || query.isBlank() || query.length() > 500)
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Query must contain 1 to 500 characters.");
        if (useWebSearch()) return searchWebResults(query);
        try {
            Map<String, Object> response = restClient.get()
                .uri("/search?part=snippet&type=video&videoCategoryId=10&maxResults=10&videoEmbeddable=true&videoSyndicated=true&q={query}&key={key}", query.trim(), apiKey)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null || !(response.get("items") instanceof List<?> items))
                throw new IllegalArgumentException();
            var songs = new java.util.ArrayList<SongDto>();
            var seen = new java.util.HashSet<String>();
            for (Object value : items) {
                Map<?, ?> item = asMap(value), snippet = asMap(item.get("snippet"));
                String id = text(asMap(item.get("id")).get("videoId"));
                String title = text(snippet.get("title")), artist = text(snippet.get("channelTitle"));
                if (!id.matches("[A-Za-z0-9_-]{11}") || title.isBlank() || title.length() > 255
                    || artist.isBlank() || artist.length() > 255
                    || "live".equals(text(snippet.get("liveBroadcastContent")))) continue;
                String thumbnail = "";
                Map<?, ?> thumbnails = asMap(snippet.get("thumbnails"));
                for (String size : List.of("high", "medium", "default")) {
                    String candidate = text(asMap(thumbnails.get(size)).get("url"));
                    if (validThumbnail(candidate)) { thumbnail = candidate; break; }
                }
                if (!validThumbnail(thumbnail) || !seen.add(id)) continue;
                songs.add(new SongDto(title, artist, id, thumbnail));
                if (songs.size() == 3) break;
            }
            return songs.isEmpty() ? searchWebResults(query) : List.copyOf(songs);
        } catch (RestClientException | IllegalArgumentException exception) {
            if (exception instanceof RestClientException failure) rememberQuotaFailure(failure);
            return searchWebResults(query);
        }
    }

    public SongDto searchSong(String query) {
        if (query == null || query.isBlank() || query.length() > 500) return null;
        String key = cacheKey(query);
        SongDto cached = songCache.get(key);
        if (cached != null) return cached;
        for (String variant : List.of(query.trim(), query.trim() + " official audio", query.replace(" - ", " ") + " music")) {
            SongDto song = useWebSearch() ? null : searchSongOnce(variant);
            if (song == null) song = searchViaWebScrape(variant);
            if (song != null) { songCache.put(key, song); return song; }
        }
        return null;
    }

    SongDto searchSongOnce(String query) {
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
            if (exception instanceof RestClientException failure) rememberQuotaFailure(failure);
        }
        return null;
    }

    public SongDto searchViaWebScrape(String query) {
        List<SongDto> songs = searchWebResults(query);
        return songs.isEmpty() ? null : songs.get(0);
    }

    private List<SongDto> searchWebResults(String query) {
        if (query == null || query.isBlank() || query.length() > 550) return List.of();
        String key = cacheKey(query);
        List<SongDto> cached = webCache.get(key);
        if (cached != null) return cached;
        try {
            String html = restClient.get()
                .uri("https://www.youtube.com/results?search_query={query}", query.trim())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "text/html")
                .retrieve().body(String.class);
            List<SongDto> songs = parseWebResults(html, query);
            if (!songs.isEmpty()) {
                if (webCache.size() >= 2000) webCache.clear();
                if (songCache.size() >= 2000) songCache.clear();
                webCache.put(key, songs); songCache.put(key, songs.get(0));
            }
            return songs;
        } catch (RestClientException | IllegalArgumentException failure) { return List.of(); }
    }

    // Match renderer boundaries rather than pairing unrelated page-wide ID/title matches.
    static List<SongDto> parseWebResults(String html, String query) {
        if (html == null || html.isBlank()) return List.of();
        var songs = new java.util.ArrayList<SongDto>();
        var seen = new java.util.HashSet<String>();
        var matcher = java.util.regex.Pattern.compile("\"videoRenderer\"\\s*:\\s*\\{").matcher(html);
        while (matcher.find() && songs.size() < 3) {
            int start = matcher.end() - 1;
            int depth = 0; boolean quoted = false, escaped = false;
            for (int end = start; end < html.length(); end++) {
                char c = html.charAt(end);
                if (quoted) {
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == '"') quoted = false;
                    continue;
                }
                if (c == '"') quoted = true;
                else if (c == '{') depth++;
                else if (c == '}' && --depth == 0) {
                    try {
                        var video = JSON.readTree(html.substring(start, end + 1));
                        String id = video.path("videoId").asText();
                        String title = rendererText(video.path("title"));
                        String artist = rendererText(video.path("ownerText"));
                        if (artist.isBlank()) artist = rendererText(video.path("longBylineText"));
                        int separator = query.indexOf(" - ");
                        if (separator > 0) artist = query.substring(0, separator).trim();
                        if (artist.isBlank()) artist = "YouTube";
                        if (id.matches("[A-Za-z0-9_-]{11}") && !title.isBlank() && title.length() <= 255
                            && artist.length() <= 255 && seen.add(id))
                            songs.add(new SongDto(title, artist, id, "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg"));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { /* Try the next renderer. */ }
                    break;
                }
            }
        }
        return List.copyOf(songs);
    }
    private static String rendererText(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.path("simpleText").isTextual()) return node.path("simpleText").asText();
        var text = new StringBuilder();
        for (var run : node.path("runs")) text.append(run.path("text").asText());
        return text.toString();
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
