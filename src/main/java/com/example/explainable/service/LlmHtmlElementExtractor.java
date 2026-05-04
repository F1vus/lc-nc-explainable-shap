package com.example.explainable.service;

import com.example.explainable.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmHtmlElementExtractor {

    private final LlmClient llmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> extract(String htmlAndCss) {

        if ((htmlAndCss == null || htmlAndCss.isBlank())) {

            return List.of();

        }

                    String prompt = """
            
            You are an HTML/CSS UI element extractor.
            
            Your goal:
            
            Extract ONLY visible, user-facing UI components from the BODY of the page.
            
            STRICT RULES:
            
            - Ignore ALL technical/non-UI tags:
            
            html, head, title, meta, link, script, style
            
            - Focus ONLY on elements users interact with or see
            
            - Prefer elements inside <body>
            
            - Ignore document structure wrappers unless they represent UI (e.g. header, nav, main, footer are OK)
            
            PRIORITIZE:
            
            - forms (login, register)
            
            - inputs (text, password, search)
            
            - buttons
            
            - lists (ul, ol)
            
            - cards, sections
            
            - navigation (nav, menu)
            
            - dashboard elements
            
            - containers with meaningful class names
            
            FORMAT:
            
            - Return canonical selector-like labels
            
            - Prefer tag.class if available:
            
            form.login-form
            
            button.submit-btn
            
            ul.todo-list
            
            - If no class → use semantic tag:
            
            header, nav, main, footer
            
            LIMIT:
            
            - Max 15 elements
            
            - No duplicates
            
            - No explanations
            
            Return ONLY JSON:
            
            {
            
            "elements": [
            
            "example"
            
            ]
            
            }
            
            HTML and CSS:
            
            %s
            
            """.formatted(htmlAndCss);

        String response = llmClient.callLlm(prompt);

        return parseElements(response);

    }

    private List<String> parseElements(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        try {
            String cleanedJson = response.trim();

            if (cleanedJson.contains("{")) {
                cleanedJson = cleanedJson.substring(cleanedJson.indexOf("{"), cleanedJson.lastIndexOf("}") + 1);
            }

            JsonNode root = objectMapper.readTree(cleanedJson);
            JsonNode elementsNode = root.get("elements");

            if (elementsNode == null || !elementsNode.isArray()) {
                return fallbackParse(response);
            }

            List<String> result = new ArrayList<>();
            for (JsonNode node : elementsNode) {
                if (node != null && node.isTextual()) {
                    String value = node.asText().trim();
                    if (!value.isBlank()) {
                        result.add(normalizeLabel(value));
                    }
                }
            }

            return result.stream().distinct().collect(Collectors.toList());
        } catch (Exception e) {
            log.error("JSON parsing failed, switching to fallback. Raw response: {}", response);
            return fallbackParse(response);
        }
    }

    private List<String> fallbackParse(String response) {

        List<String> result = new ArrayList<>();

        for (String line : response.split("\\R")) {

            String cleaned = line

                    .replaceAll("^[\\s\\-•\\d.]+", "")

                    .trim();

            if (!cleaned.isBlank()) {

                result.add(normalizeLabel(cleaned));

            }

        }

        return result.stream().distinct().toList();

    }

    private String normalizeLabel(String label) {

        String s = label.trim().toLowerCase(Locale.ROOT);

        s = s.replace("\"", "");

        s = s.replace("'", "");

        s = s.replaceAll("\\s+", " ");

        return s;

    }

}

