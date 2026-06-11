package com.example.explainable.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiClient {

    private static final String GEMINI_MODEL = "gemini-2.5-flash";

    private final WebClient webClient;
    private final String apiKey;

    public GeminiClient(
            WebClient.Builder builder,
            @Value("${gemini.base.url}") String baseUrl,
            @Value("${gemini.api.key}") String apiKey
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public String callLlm(String prompt) {
        long startNs = System.nanoTime();

        // Gemini uses a different request structure than OpenAI-compatible APIs
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 2048
                )
        );

        log.info("Gemini LLM request started, model={}", GEMINI_MODEL);
        log.debug("Gemini prompt length={}", prompt == null ? 0 : prompt.length());

        try {
            String url = "/v1beta/models/" + GEMINI_MODEL + ":generateContent?key=" + apiKey;

            Map response = webClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(60));

            long tookMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();

            if (response == null) {
                log.warn("Gemini response is null after {} ms", tookMs);
                return "";
            }

            log.info("Gemini response received in {} ms", tookMs);

            // Gemini response: candidates[0].content.parts[0].text
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                log.warn("Gemini response has no candidates. Full response: {}", response);
                return "";
            }

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content == null) {
                log.warn("Gemini candidate has no content");
                return "";
            }

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                log.warn("Gemini content has no parts");
                return "";
            }

            String text = parts.get(0).get("text").toString().trim();
            log.debug("Gemini response length={}", text.length());
            return text;

        } catch (Exception e) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
            log.error("Gemini request failed after {} ms: {}", tookMs, e.getMessage(), e);
            throw e;
        }
    }
}