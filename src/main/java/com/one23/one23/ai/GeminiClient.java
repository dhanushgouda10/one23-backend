
package com.one23.one23.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.List;
import java.util.Map;

@Service
public class GeminiClient {

    private final RestClient http;
    private final String chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Set up the Gemini connection
    public GeminiClient(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.chat-model}") String chatModel) {

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);

        http = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", apiKey)
                .requestFactory(factory)
                .build();

        this.chatModel = chatModel;
    }

    // Send a prompt to Gemini and return its answer
    public String generate(String system, String user, boolean asJson) {

        Map<String, Object> config = Map.of(
                "temperature", 0
        );

        if (asJson) {
            config = Map.of(
                    "temperature", 0,
                    "responseMimeType", "application/json"
            );
        }

        Map<String, Object> body = Map.of(
                "systemInstruction",
                Map.of("parts", List.of(Map.of("text", system))),

                "contents",
                List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", user))
                )),

                "generationConfig", config
        );

        try {
            // Send the HTTP request to Gemini
            String response = http.post()
                    .uri("/models/{model}:generateContent", chatModel)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            // Read Gemini's JSON response
            JsonNode root = objectMapper.readTree(response);

            // Extract and return the generated text
            return root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText();

        } catch (Exception e) {
            throw new AiUnavailableException(
                    "Gemini chat call failed", e);
        }
    }
}
