package com.example.explainable.service;

import com.example.explainable.client.LlmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class LlmFragmentMappingService {

    private final LlmClient llmClient;

    public String mapToUiElement(String fragment, String generatedOutput) {
        String prompt = """
            You are an assistant that maps a prompt fragment to the most relevant concrete UI element
            in a generated HTML/CSS interface.

            Return ONLY one short label that is directly tied to the actual code.
            Prefer:
            - exact HTML tag with class if available, e.g. form.login-form, ul.todo-list, button.add-todo
            - CSS selector-like labels, e.g. .login-form, .todo-list, .dark-mode-toggle
            - semantic section names only if no exact class exists, e.g. hero section, sidebar, header

            Rules:
            - Do NOT return abstract words like "login" or "theme" if a concrete element exists
            - Do NOT explain
            - Do NOT return multiple labels
            - Do NOT invent elements that are not present
            - Keep the answer short and code-like

            Fragment:
            "%s"

            Generated UI output:
            %s

            Return only the best matching UI element label.
            """.formatted(fragment, generatedOutput);

        return llmClient.callLlm(prompt).trim();
    }
}