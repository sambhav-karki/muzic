package com.butterzhub.muzic.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class YouTubeClientTests {
    private MockRestServiceServer server;
    private YouTubeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YouTubeClient(builder);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
    }

    @Test
    void mapsNestedMetadataAndEncodesQuery() {
        server.expect(queryParam("q", "Miles%20Davis%20-%20So%20What"))
                .andExpect(queryParam("part", "snippet"))
                .andExpect(queryParam("maxResults", "1"))
                .andExpect(queryParam("type", "video"))
                .andExpect(queryParam("videoEmbeddable", "true"))
                .andRespond(withSuccess("""
                        {"items":[{"id":{"videoId":"abcDEFG_123"},"snippet":{
                        "title":"So What (Official)","channelTitle":"Jazz Channel",
                        "thumbnails":{"medium":{"url":"https://example.com/cover.jpg"}}}}]}
                        """, MediaType.APPLICATION_JSON));
        var song = client.searchSong("Miles Davis - So What");
        assertEquals("Miles Davis", song.artist());
        assertEquals("So What (Official)", song.title());
        assertEquals("abcDEFG_123", song.youtubeVideoId());
        assertEquals("https://example.com/cover.jpg", song.thumbnailUrl());
        server.verify();
    }

    @Test
    void usesChannelAndStaticThumbnailWhenQueryHasNoArtist() {
        server.expect(anything()).andRespond(withSuccess("""
                {"items":[{"id":{"videoId":"abcDEFG_123"},"snippet":{
                "title":"Jazz", "channelTitle":"Jazz Channel"}}]}
                """, MediaType.APPLICATION_JSON));
        var song = client.searchSong("jazz");
        assertEquals("Jazz Channel", song.artist());
        assertTrue(song.thumbnailUrl().contains("abcDEFG_123"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"items\":[]}", "{\"items\":[null]}", "not JSON",
            "{\"items\":[{\"id\":{\"videoId\":\"bad\"}}]}"})
    void malformedOrEmptySearchFallsBack(String response) {
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertFallback();
    }

    @Test
    void quotaFailureFallsBack() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertFallback();
    }

    @Test
    void networkTimeoutFallsBack() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("timeout")));
        assertFallback();
    }

    @Test
    void missingKeyAvoidsNetwork() {
        ReflectionTestUtils.setField(client, "apiKey", "");
        assertFallback();
    }

    private void assertFallback() {
        var song = client.searchSong("jazz");
        assertEquals("Never Gonna Give You Up", song.title());
        assertEquals("Rick Astley", song.artist());
        assertEquals("dQw4w9WgXcQ", song.youtubeVideoId());
        server.verify();
    }
}
