package com.butterzhub.muzic.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
    private static final List<String> FALLBACK = List.of("Tycho - Awake",
            "Lofi Fruits Music - Chill Lofi Study", "Miles Davis - So What");
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
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=test-key"))
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
        for (int i = 0; i < 16; i++) {
        server.expect(anything()).andRespond(withSuccess(envelope(text), MediaType.APPLICATION_JSON));
        }
        assertEquals(FALLBACK, client.suggestSongs("jazz"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"candidates\":[]}", "not JSON"})
    void missingOrMalformedProviderResponseFallsBack(String response) {
        for (int i = 0; i < 16; i++) {
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        }
        assertEquals(FALLBACK, client.suggestSongs("jazz"));
        server.verify();
    }

    @Test
    void providerFailureFallsBack() {
        for (int i = 0; i < 16; i++) {
        server.expect(anything()).andRespond(withServerError());
        }
        assertEquals(FALLBACK, client.suggestSongs("jazz"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {503, 429, 404})
    void capacityOrMissingModelTriesNextModel(int status) throws Exception {
        server.expect(requestTo(modelUrl("gemini-3.1-flash-lite")))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        server.expect(requestTo(modelUrl("gemini-3.5-flash-lite")))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("jazz"))
                .andRespond(withSuccess(envelope("[\"A - B\",\"C - D\",\"E - F\"]"), MediaType.APPLICATION_JSON));

        assertEquals(List.of("A - B", "C - D", "E - F"), client.suggestSongs("jazz"));
        server.verify();
    }

    @Test
    void exhaustedChainReturnsStarterSongs() {
        List<String> models = List.of(
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-3-flash",
            "gemini-3.5-flash",
            "gemini-3.6-flash",
            "gemini-3.7-flash",
            "gemini-3.8-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash",
            "gemini-1.5-flash-8b",
            "gemma-4-31b-it",
            "gemma-4-26b-it",
            "gemini-1.5-pro");
        for (String model : models) {
            server.expect(requestTo(modelUrl(model))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        }
        assertEquals(FALLBACK, client.suggestSongs("jazz"));
        server.verify();
    }

    @Test
    void emptyResponseTriesNextModel() throws Exception {
        server.expect(requestTo(modelUrl("gemini-3.1-flash-lite")))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        server.expect(requestTo(modelUrl("gemini-3.5-flash-lite")))
                .andRespond(withSuccess(envelope("[\"A - B\",\"C - D\",\"E - F\"]"), MediaType.APPLICATION_JSON));
        assertEquals(List.of("A - B", "C - D", "E - F"), client.suggestSongs("jazz"));
        server.verify();
    }
    private String modelUrl(String model) {
        return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=test-key";
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
        for (int i = 0; i < 16; i++) {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("timeout")));
        }
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

