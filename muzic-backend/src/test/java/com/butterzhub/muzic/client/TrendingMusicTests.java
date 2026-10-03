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

class TrendingMusicTests {
    private YouTubeClient client;
    private MockRestServiceServer server;
    @BeforeEach void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YouTubeClient(builder);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
    }
    @Test void requestsMusicChartAndRejectsUnavailableVideos() {
        server.expect(queryParam("chart", "mostPopular"))
            .andExpect(queryParam("videoCategoryId", "10"))
            .andExpect(queryParam("regionCode", "US"))
            .andExpect(queryParam("part", "snippet,status"))
            .andExpect(queryParam("maxResults", "20"))
            .andRespond(withSuccess("""
                {"items":[
                  {"id":"abcdefghijk","status":{"privacyStatus":"public","uploadStatus":"processed","embeddable":true},"snippet":{"title":"Music","channelTitle":"Artist"}},
                  {"id":"bad","status":{"privacyStatus":"public","uploadStatus":"processed","embeddable":true},"snippet":{"title":"Bad","channelTitle":"Artist"}},
                  {"id":"12345678901","status":{"privacyStatus":"private","uploadStatus":"processed","embeddable":true},"snippet":{"title":"Private","channelTitle":"Artist"}},
                  {"id":"12345678902","status":{"privacyStatus":"public","uploadStatus":"processed","embeddable":false},"snippet":{"title":"Blocked","channelTitle":"Artist"}},
                  {"id":"12345678903","status":{"privacyStatus":"public","uploadStatus":"deleted","embeddable":true},"snippet":{"title":"Deleted","channelTitle":"Artist"}}
                ]}
                """, MediaType.APPLICATION_JSON));
        var songs = client.trendingMusic();
        assertEquals(1, songs.size());
        assertEquals("abcdefghijk", songs.getFirst().youtubeVideoId());
        assertEquals("Artist", songs.getFirst().artist());
        server.verify();
    }
    @Test void missingKeyIsActionableAndDoesNotUseNetwork() {
        ReflectionTestUtils.setField(client, "apiKey", "");
        var error = assertThrows(ResponseStatusException.class, client::trendingMusic);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertTrue(error.getReason().contains("YOUTUBE_API_KEY"));
        server.verify();
    }
    @Test void quotaFailureDoesNotInventTrendingSongs() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        var error = assertThrows(ResponseStatusException.class, client::trendingMusic);
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertTrue(error.getReason().contains("quota"));
        server.verify();
    }
}
