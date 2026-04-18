package com.example.explainable.service;

import com.example.explainable.client.LlmClient;
import com.example.explainable.model.GeneratedUi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class LlmHtmlGenerationService {
    private final LlmClient llmClient;

    public GeneratedUi generate(String prompt) {
        String fullPrompt = """
                You are a system that generates a COMPLETE, STRUCTURED HTML/CSS user interface\s
                based strictly on a user prompt.
            
                OUTPUT FORMAT (STRICT):
                - Return ONLY valid HTML (no markdown, no explanations)
                - Include embedded CSS inside a <style> tag
                - The HTML must be complete (<html>, <head>, <body>)
                - Use semantic and reusable class names
                - The design must be visually structured and readable
           
                GENERATION RULES (VERY IMPORTANT):
                - Only include UI elements that are directly supported by the prompt
                - DO NOT invent features that are not mentioned
                - DO NOT add extra functionality beyond the prompt
                - DO NOT explain anything
                - DO NOT include comments
                - Use consistent layout and spacing
                - Keep the design realistic and minima
            
                DESIGN BEHAVIOR:
                - Interpret the user prompt as requirements for UI structure and appearance
                - Choose layout, components, and styling based on the meaning of the prompt
                - Use appropriate UI patterns (forms, lists, cards, navigation, etc.)
                - Adapt styling (colors, layout, components) to match the intent of the prompt
            
                USER PROMPT:
                "%s"
            """.formatted(prompt);

        String html = llmClient.callLlm(fullPrompt);
        return new GeneratedUi(html, "AI Generated UI");
    }
}
