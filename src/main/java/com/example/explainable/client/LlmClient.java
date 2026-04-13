package com.example.explainable.client;

import com.example.explainable.dto.llm_call.ChatRequest;
import com.example.explainable.dto.llm_call.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class LlmClient {

    private final WebClient webClient;

    private final String apiKey;

    public LlmClient(WebClient.Builder builder, @Value("${qroq.base.url}") final String baseUrl, @Value("${groq.api.key}") final String apiKey) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .build();

        this.apiKey = apiKey;
    }

    public String callLlm(String prompt) {

        ChatRequest request = new ChatRequest(
                "llama-3.3-70b-versatile",
                List.of(new Message("user", prompt)),
                0.7
        );

        Map response = webClient.post()
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

        return message.get("content").toString().trim();
    }
}