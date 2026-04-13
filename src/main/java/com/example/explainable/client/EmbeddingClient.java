package com.example.explainable.client;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingClient {

    private final WebClient webClient;

    public EmbeddingClient(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("http://localhost:5000")
                .build();
    }

    public double[] embed(String text) {

        Map<String, String> request = Map.of("text", text);

        Map response = webClient.post()
                .uri("/embed")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Double> embedding = (List<Double>) response.get("embedding");

        return embedding.stream().mapToDouble(Double::doubleValue).toArray();
    }
}