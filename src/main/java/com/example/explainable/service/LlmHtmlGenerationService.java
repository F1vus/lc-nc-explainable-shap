package com.example.explainable.service;

import com.example.explainable.client.LlmClient;
import com.example.explainable.model.GeneratedUi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class LlmHtmlGenerationService {
    private final LlmClient llmClient;

    public GeneratedUi generate(String prompt) {
        String fullPrompt = """
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
                """.formatted(prompt);

        String response = llmClient.callLlm(fullPrompt);

        String html = extractSection(response, "HTML:", "SUMMARY:");
        String summary = extractAfterLabel(response, "SUMMARY:");

        if (html.isBlank()) {
            html = response; // fallback if model ignored format
        }
        if (summary.isBlank()) {
            summary = "Generated UI based on the provided prompt.";
        }
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