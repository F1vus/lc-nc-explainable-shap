package com.example.explainable.service.impl;

import com.example.explainable.service.IPromptFragmentExtractor;
import com.example.explainable.client.LlmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmPromptFragmentExtractor implements IPromptFragmentExtractor{

    private final LlmClient llmClient;

    @Override
    public List<String> extract(String prompt) {

        String helperPrompt = """
            Extract distinct requirement fragments from the user prompt BELOW.

            STRICT RULES:
            - DO NOT translate the text.
            - DO NOT paraphrase.
            - DO NOT reorder.
            - DO NOT add or remove words.
            - DO NOT merge ideas.
            - Only split the user's text into meaningful requirement fragments.
            - Output ONLY the fragments, each on a new line.
            - NO bullets, NO numbers, NO explanations.

            User prompt:
            """ + prompt;

        String response = llmClient.callLlm(helperPrompt);

        return Arrays.stream(response.split("\n"))
                .map(line -> line.replaceAll("^[-•\\d. ]+", "").trim())
                .filter(s -> !s.isBlank())
                .toList();
    }
}