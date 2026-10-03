package com.butterzhub.muzic.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LlmClientTests {
    private static final List<String> FALLBACK = List.of("The Beatles - Hey Jude",
            "Queen - Bohemian Rhapsody", "Michael Jackson - Billie Jean");
    private MockRestServiceServer server;
    private LlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LlmClient(builder);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
    }

    @Test
    void sendsPreferencesSeparatelyAndParsesSongSearches() throws Exception {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"))
                .andExpect(header("X-goog-api-key", "test-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("relaxing jazz"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").exists())
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andRespond(withSuccess(envelope("[\"Miles Davis - So What\",\"John Coltrane - Naima\",\"Bill Evans - Peace Piece\"]"), MediaType.APPLICATION_JSON));

        assertEquals(List.of("Miles Davis - So What", "John Coltrane - Naima",
                "Bill Evans - Peace Piece"), client.suggestSongs("relaxing jazz"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "[]", "not JSON", "```json\n[]\n```", "[1,2,3]",
            "[\"A - B\",\"C - D\",\"\"]", "[\"A - B\",\"A - B\",\"C - D\"]",
            "[\"A\",\"B\",\"C\"]"})
    void invalidModelOutputFallsBack(String text) throws Exception {
        server.expect(anything()).andRespond(withSuccess(envelope(text), MediaType.APPLICATION_JSON));
        assertEquals(FALLBACK, client.suggestSongs("jazz"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"candidates\":[]}", "not JSON"})
    void missingOrMalformedProviderResponseFallsBack(String response) {
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertEquals(FALLBACK, client.suggestSongs("jazz"));
        server.verify();
    }

    @Test
    void providerFailureFallsBack() {
        server.expect(anything()).andRespond(withServerError());
        assertEquals(FALLBACK, client.suggestSongs("jazz"));
        server.verify();
    }

    @Test
    void ignoresThoughtsAndJoinsVisibleParts() throws Exception {
        String response = JsonMapper.builder().build().writeValueAsString(Map.of("candidates", List.of(
                Map.of("finishReason", "STOP", "content", Map.of("parts", List.of(
                        Map.of("thought", true, "text", "private reasoning"),
                        Map.of("text", "[\"A - B\",\"C - D\","),
                        Map.of("text", "\"E - F\"]")))))));
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertEquals(List.of("A - B", "C - D", "E - F"), client.suggestSongs("jazz"));
        server.verify();
    }

    @Test
    void networkTimeoutFallsBack() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("timeout")));
        assertEquals(FALLBACK, client.suggestSongs("jazz"));
        server.verify();
    }

    @Test
    void missingKeyUsesFallbackWithoutNetwork() {
        ReflectionTestUtils.setField(client, "apiKey", "");
        assertEquals(FALLBACK, client.suggestSongs("jazz"));
        server.verify();
    }

    private String envelope(String text) throws JsonProcessingException {
        return JsonMapper.builder().build().writeValueAsString(Map.of("candidates", List.of(
                Map.of("finishReason", "STOP", "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }
}

