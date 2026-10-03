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
import static org.junit.jupiter.api.Assertions.assertNull;
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
                .andExpect(queryParam("maxResults", "5"))
                .andExpect(queryParam("type", "video"))
                .andExpect(queryParam("videoEmbeddable", "true"))
                .andRespond(withSuccess("""
                        {"items":[{"id":{"videoId":"abcDEFG_123"},"snippet":{
                        "title":"So What (Official)","channelTitle":"Jazz Channel",
                        "thumbnails":{"medium":{"url":"https://example.com/cover.jpg"}}}}]}
                        """, MediaType.APPLICATION_JSON));
        expectVideo("https://example.com/cover.jpg");
        var song = client.searchSong("Miles Davis - So What");
        assertEquals("Miles Davis", song.artist());
        assertEquals("So What (Official)", song.title());
        assertEquals("abcDEFG_123", song.youtubeVideoId());
        assertEquals("https://example.com/cover.jpg", song.thumbnailUrl());
        server.verify();
    }

    @Test
    void usesVerifiedChannelAndThumbnailWhenQueryHasNoArtist() {
        server.expect(anything()).andRespond(withSuccess("""
                {"items":[{"id":{"videoId":"abcDEFG_123"},"snippet":{
                "title":"Jazz", "channelTitle":"Jazz Channel"}}]}
                """, MediaType.APPLICATION_JSON));
        expectVideo("https://i.ytimg.com/vi/abcDEFG_123/hqdefault.jpg");
        var song = client.searchSong("jazz");
        assertEquals("Jazz Channel", song.artist());
        assertTrue(song.thumbnailUrl().contains("abcDEFG_123"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"items\":[]}", "{\"items\":[null]}", "not JSON",
            "{\"items\":[{\"id\":{\"videoId\":\"bad\"}}]}"})
    void malformedOrEmptySearchIsSkipped(String response) {
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertSkipped();
    }

    @Test
    void quotaFailureIsSkipped() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertSkipped();
    }

    @Test
    void networkTimeoutIsSkipped() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("timeout")));
        assertSkipped();
    }

    @Test
    void retriesAnEmptyLookupWithOfficialAudioQuery() {
        server.expect(anything()).andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.youtube.com/results?search_query=jazz")).andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML));
        server.expect(queryParam("q", "jazz%20official%20audio")).andRespond(withSuccess(
            "{\"items\":[{\"id\":{\"videoId\":\"abcDEFG_123\"}}]}", MediaType.APPLICATION_JSON));
        expectVideo("https://i.ytimg.com/vi/abcDEFG_123/hqdefault.jpg");
        assertEquals("abcDEFG_123", client.searchSong("jazz").youtubeVideoId());
        server.verify();
    }

    @Test
    void missingKeyAvoidsNetwork() {
        ReflectionTestUtils.setField(client, "apiKey", "");
        assertSkipped();
    }

    private void expectVideo(String thumbnail) {
        server.expect(queryParam("part", "snippet,status"))
                .andExpect(queryParam("id", "abcDEFG_123"))
                .andRespond(withSuccess("""
                    {"items":[{"id":"abcDEFG_123","status":{"privacyStatus":"public",
                    "uploadStatus":"processed","embeddable":true},"snippet":{
                    "title":"So What (Official)","channelTitle":"Jazz Channel",
                    "thumbnails":{"medium":{"url":"%s"}}}}]}
                    """.formatted(thumbnail), MediaType.APPLICATION_JSON));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"items\":[]}",
        "{\"items\":[{\"id\":\"abcDEFG_123\",\"status\":{\"privacyStatus\":\"private\"}}]}",
        "{\"items\":[{\"id\":\"different12\"}]}", "not JSON"})
    void skipsUnavailableOrMalformedVerification(String response) {
        server.expect(anything()).andRespond(withSuccess(
            "{\"items\":[{\"id\":{\"videoId\":\"abcDEFG_123\"}}]}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertSkipped();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/image.jpg", "https:///missing-host", "bad url", ""})
    void skipsInvalidThumbnailMetadata(String thumbnail) {
        server.expect(anything()).andRespond(withSuccess(
            "{\"items\":[{\"id\":{\"videoId\":\"abcDEFG_123\"}}]}", MediaType.APPLICATION_JSON));
        expectVideo(thumbnail);
        assertSkipped();
    }

    private void assertSkipped() {
        assertNull(client.searchSongOnce("jazz"));
        server.verify();
    }

    @Test
    void selectsLaterVerifiedResultWhenFirstWasRemoved() {
        server.expect(anything()).andRespond(withSuccess("""
            {"items":[{"id":{"videoId":"missing1234"}},
                      {"id":{"videoId":"abcDEFG_123"}}]}
            """, MediaType.APPLICATION_JSON));
        server.expect(queryParam("id", "missing1234%2CabcDEFG_123"))
                .andRespond(withSuccess("""
                    {"items":[{"id":"abcDEFG_123","status":{"privacyStatus":"public",
                    "uploadStatus":"processed","embeddable":true},"snippet":{
                    "title":"Song","channelTitle":"Artist","thumbnails":{
                    "high":{"url":"http://invalid.example/image.jpg"},
                    "medium":{"url":"https://i.ytimg.com/image.jpg"}}}}]}
                    """, MediaType.APPLICATION_JSON));
        var song = client.searchSong("jazz");
        assertEquals("abcDEFG_123", song.youtubeVideoId());
        assertEquals("https://i.ytimg.com/image.jpg", song.thumbnailUrl());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "\"privacyStatus\":\"private\",\"uploadStatus\":\"processed\",\"embeddable\":true",
        "\"privacyStatus\":\"public\",\"uploadStatus\":\"deleted\",\"embeddable\":true",
        "\"privacyStatus\":\"public\",\"uploadStatus\":\"processed\",\"embeddable\":false"})
    void rejectsInactiveOrNonEmbeddableVideos(String status) {
        server.expect(anything()).andRespond(withSuccess(
            "{\"items\":[{\"id\":{\"videoId\":\"abcDEFG_123\"}}]}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess("""
            {"items":[{"id":"abcDEFG_123","status":{%s},"snippet":{
            "title":"Song","channelTitle":"Artist",
            "thumbnails":{"high":{"url":"https://i.ytimg.com/image.jpg"}}}}]}
            """.formatted(status), MediaType.APPLICATION_JSON));
        assertSkipped();
    }
}
