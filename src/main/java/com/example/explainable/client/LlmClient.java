package com.example.explainable.client;

import com.example.explainable.dto.llm_call.ChatRequest;
import com.example.explainable.dto.llm_call.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;


@Slf4j
@Service
public class LlmClient {

    private final WebClient webClient;
    private final String apiKey;

    public LlmClient(
            WebClient.Builder builder,
            @Value("${groq.base.url}") final String baseUrl,
            @Value("${groq.api.key}") final String apiKey
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public String callLlm(String prompt) {
        long startNs = System.nanoTime();

        ChatRequest request = new ChatRequest(
                "llama-3.3-70b-versatile",
                List.of(new Message("user", prompt)),
                0.7
        );

        log.info("LLM request started");
        log.debug("LLM request model={}, temperature={}, promptLength={}",
                request.getModel(),
                request.getTemperature(),
                prompt == null ? 0 : prompt.length());

        if (prompt != null) {
            log.trace("LLM prompt:\n{}", prompt);
        }

        try {
            Map response = webClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(60));

            long tookMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();

            if (response == null) {
                log.warn("LLM response is null after {} ms", tookMs);
                return "";
            }

            log.info("LLM response received in {} ms", tookMs);
            log.debug("LLM raw response keys: {}", response.keySet());

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("LLM response has no choices. Full response: {}", response);
                return "";
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null || message.get("content") == null) {
                log.warn("LLM response has no message/content. Full response: {}", response);
                return "";
            }

            String content = message.get("content").toString().trim();
            log.debug("LLM response content length={}", content.length());
            log.trace("LLM response content:\n{}", content);

            return content;
        } catch (Exception e) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
            log.error("LLM request failed after {} ms: {}", tookMs, e.getMessage(), e);
            throw e;
        }
    }
}