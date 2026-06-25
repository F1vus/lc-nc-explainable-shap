package com.example.explainable.service;

import com.example.explainable.client.GeminiClient;
import com.example.explainable.client.LlmClient;
import com.example.explainable.model.GeneratedUi;
import com.example.explainable.model.LlmProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class LlmHtmlGenerationService {
    private final LlmClient llmClient;
    private final GeminiClient geminiClient;

    private static final String PROMPT_TEMPLATE = """
            You are a system that generates a COMPLETE, STRUCTURED HTML/CSS user interface
            based strictly on a user prompt.

            OUTPUT FORMAT (STRICT):
            Return the response in exactly this structure:

            HTML:
            <complete valid HTML here>

            SUMMARY:
            <one short sentence describing the generated UI>

            RULES:
            - HTML must be complete: <!doctype html>, <html>, <head>, <body>
            - Include embedded CSS inside a <style> tag
            - Return only HTML in the HTML section
            - Do not add markdown fences
            - Do not add explanations outside SUMMARY
            - SUMMARY must be 1 short sentence
            - Do not invent features not supported by the prompt

            USER PROMPT:
            "%s"
            """;

    public GeneratedUi generate(String prompt) {return generate(prompt, LlmProvider.GROQ);}

    public GeneratedUi generate(String prompt, LlmProvider provider) {
        String fullPrompt = PROMPT_TEMPLATE.formatted(prompt);

        log.info("Generating UI with provider={}", provider);
        String response = switch (provider) {
            case GROQ   -> llmClient.callLlm(fullPrompt);
            case GEMINI -> geminiClient.callLlm(fullPrompt);
        };

        String html = extractSection(response, "HTML:", "SUMMARY:");
        String summary = extractAfterLabel(response, "SUMMARY:");

        if (html.isBlank()) html = response;
        if (summary.isBlank()) summary = "Generated UI based on the provided prompt.";

        log.info("Summary: {}", summary);
        return new GeneratedUi(html.trim(), summary.trim(), "AI Generated UI");
    }

    private String extractSection(String text, String startLabel, String endLabel) {
        int start = text.indexOf(startLabel);
        if (start < 0) return "";
        start += startLabel.length();
        int end = text.indexOf(endLabel, start);
        if (end < 0) return text.substring(start).trim();
        return text.substring(start, end).trim();
    }

    private String extractAfterLabel(String text, String label) {
        int start = text.indexOf(label);
        if (start < 0) return "";
        start += label.length();
        return text.substring(start).trim();
    }
}