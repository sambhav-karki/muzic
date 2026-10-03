package com.butterzhub.muzic.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DirectMusicSearchTests {
    private YouTubeClient client;
    private MockRestServiceServer server;
    @BeforeEach void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YouTubeClient(builder);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
    }

    private String item(String id, String title, String thumbnail) {
        return "{\"id\":{\"videoId\":\"" + id + "\"},\"snippet\":{\"title\":\"" + title
            + "\",\"channelTitle\":\"Artist\",\"thumbnails\":{\"high\":{\"url\":\"" + thumbnail + "\"}}}}";
    }

    @Test void filtersMusicMetadataAndReturnsFirstThreeUniqueTracks() {
        String valid = "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg";
        server.expect(queryParam("q", "song%20%26%20artist"))
            .andExpect(queryParam("part", "snippet")).andExpect(queryParam("type", "video"))
            .andExpect(queryParam("videoCategoryId", "10")).andExpect(queryParam("maxResults", "10"))
            .andExpect(queryParam("videoEmbeddable", "true"))
            .andRespond(withSuccess("{\"items\":[" + String.join(",",
                item("bad", "Invalid ID", valid), item("11111111111", "", valid),
                item("22222222222", "Bad thumbnail", "http://example.com/image"),
                item("abcdefghijk", "First", valid), item("abcdefghijk", "Duplicate", valid),
                item("33333333333", "Second", valid), item("44444444444", "Third", valid),
                item("55555555555", "Fourth", valid)) + "]}", MediaType.APPLICATION_JSON));
        assertEquals(java.util.List.of("First", "Second", "Third"),
            client.searchMusic("song & artist").stream().map(s -> s.title()).toList());
        server.verify();
    }

    @Test void rejectsBlankAndOversizedQueriesWithoutNetwork() {
        for (String query : java.util.List.of(" ", "x".repeat(501)))
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> client.searchMusic(query)).getStatusCode());
        server.verify();
    }

    @Test void missingKeyAndQuotaFailureUsePublicSearch() {
        server.expect(requestTo("https://www.youtube.com/results?search_query=song"))
            .andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML));
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("quotaExceeded"));
        server.expect(requestTo("https://www.youtube.com/results?search_query=song"))
            .andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML));
        ReflectionTestUtils.setField(client, "apiKey", "");
        assertTrue(client.searchMusic("song").isEmpty());
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        assertTrue(client.searchMusic("song").isEmpty());
        server.verify();
    }
}
