package com.example.explainable.service;

import com.example.explainable.client.LlmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LlmFragmentMappingService {

    private final LlmClient llmClient;

    public String mapToUiElement(String fragment, String generatedOutput) {
        String prompt = """
            You are an assistant that maps a prompt fragment to the most relevant UI element.

            Return only one short label.
            
            Fragment:
            %s

            Generated UI output:
            %s

            Answer with only the best matching label.
            """.formatted(fragment, generatedOutput);

        return llmClient.callLlm(prompt).trim().toLowerCase(Locale.ROOT);
    }
}