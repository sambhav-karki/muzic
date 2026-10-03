package com.butterzhub.muzic.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class YouTubeWebSearchTests {
    private YouTubeClient client;
    private MockRestServiceServer server;
    private static final String HTML = """
        <script>var ytInitialData = {"contents":[
          {"videoId":"unrelated12","title":"Not a video result"},
          {"videoRenderer":{"videoId":"abcdefghijk","title":{"runs":[{"text":"Song & \\\"Title\\\""}]},"ownerText":{"runs":[{"text":"Channel"}]}}},
          {"videoRenderer":{"videoId":"12345678901","title":{"runs":[{"text":"Second"}]},"ownerText":{"runs":[{"text":"Artist"}]}}}
        ]};</script>
        """;
    @BeforeEach void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YouTubeClient(builder);
        ReflectionTestUtils.setField(client, "apiKey", "");
    }
    @Test void noKeyResolvesEscapedMetadataAndCachesNormalizedQueries() {
        server.expect(requestTo("https://www.youtube.com/results?search_query=Metallica%20-%20Enter%20Sandman"))
            .andExpect(header("User-Agent", org.hamcrest.Matchers.startsWith("Mozilla/5.0")))
            .andRespond(withSuccess(HTML, MediaType.TEXT_HTML));
        var first = client.searchSong("Metallica - Enter Sandman");
        assertNotNull(first);
        assertEquals("abcdefghijk", first.youtubeVideoId());
        assertEquals("Metallica", first.artist());
        assertEquals("Song & \"Title\"", first.title());
        assertEquals(first, client.searchSong(" metallica - enter sandman "));
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", first.thumbnailUrl());
        server.verify();
    }
    @Test void quotaFailureFallsBackAndSubsequentQueriesSkipOfficialSearch() {
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        server.expect(queryParam("q", "metal"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"error\":{\"errors\":[{\"reason\":\"quotaExceeded\"}]}}"));
        server.expect(requestTo("https://www.youtube.com/results?search_query=metal"))
            .andRespond(withSuccess(HTML, MediaType.TEXT_HTML));
        server.expect(requestTo("https://www.youtube.com/results?search_query=jazz"))
            .andRespond(withSuccess(HTML, MediaType.TEXT_HTML));
        assertNotNull(client.searchSong("metal"));
        assertEquals(2, client.searchMusic("jazz").size());
        assertEquals("Channel", client.searchViaWebScrape("jazz").artist());
        server.verify();
    }
    @Test void normalSearchGetsThreeUniquePublicResultsWithoutApiKey() {
        String html = HTML.replace("]};</script>", ",{\"videoRenderer\":{\"videoId\":\"zyxwvutsrqp\",\"title\":{\"simpleText\":\"Third\"}}}]};</script>");
        server.expect(anything()).andRespond(withSuccess(html, MediaType.TEXT_HTML));
        assertEquals(3, client.searchMusic("music").size());
        server.verify();
    }
    @Test void consentPagesMalformedAndUnrelatedDataDoNotInventTracks() {
        assertTrue(YouTubeClient.parseWebResults("<html>consent required</html>", "jazz").isEmpty());
        assertTrue(YouTubeClient.parseWebResults("{\"videoId\":\"abcdefghijk\",\"title\":\"Unrelated\"}", "jazz").isEmpty());
        assertTrue(YouTubeClient.parseWebResults("{\"videoRenderer\":{\"videoId\":\"bad\"}}", "jazz").isEmpty());
    }
}
