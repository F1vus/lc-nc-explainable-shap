package com.example.explainable.service;

import com.example.explainable.client.LlmClient;
import com.example.explainable.model.GeneratedUi;
import com.example.explainable.model.LlmProvider;
import com.example.explainable.model.PromptFragment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExplanationService {

    private final LlmClient llmClient;
    private final LlmHtmlGenerationService htmlGenerationService;

    // ─── Explanation via LLM ────────────────────────────────────────────────
    public String explain(String appSummary, List<PromptFragment> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return "No prompt fragments were detected.";
        }

        List<PromptFragment> sorted = fragments.stream()
                .sorted(Comparator.comparingDouble(PromptFragment::weight).reversed())
                .toList();

        String fragmentsText = sorted.stream()
                .map(f -> String.format(
                        Locale.ROOT,
                        "- %s (score: %.3f, mapped element: %s)",
                        f.text(),
                        f.weight(),
                        f.mappedElement()
                ))
                .collect(Collectors.joining("\n"));

        String explanationPrompt = """
            You are an assistant that explains how automatically generated low-code apps behave.
            You must write short, human-centered explanations of design choices — like what a user
            would see in a tool-tip or help message. Avoid academic or analytic language.

            Given this prototype summary:
            %s

            And these feature fragments with importance scores:
            %s

            Write max 2 paragraph explaining how these features influence
            the app's behavior. Focus on what users experience — not model details or "scores".

            Now produce the natural language explanation:
            """.formatted(appSummary, fragmentsText);

        return llmClient.callLlm(explanationPrompt).trim();
    }

    // ─── Prompt refinement suggestions ──────────────────────────────────────

    public List<String> refinePromptSuggestions(String prompt) {
        return List.of(
                "Name the page type explicitly — e.g. login page, dashboard, todo app.",
                "List concrete UI components — e.g. form, sidebar, cards, table.",
                "Describe the visual theme — e.g. dark mode, minimal, modern.",
                "State the main user action — e.g. add tasks, login, register.",
                "Keep requirements separated so attribution becomes more accurate."
        );
    }

    // ─── Consistency test (simple boolean) ──────────────────────────────────
    public boolean consistencyTest(String originalPrompt, String modifiedPrompt) {
        if (modifiedPrompt == null || modifiedPrompt.isBlank()) return false;
        if (modifiedPrompt.equalsIgnoreCase(originalPrompt)) return false;
        double ratio = (double) Math.abs(
                originalPrompt.length() - modifiedPrompt.length()
        ) / Math.max(originalPrompt.length(), 1);
        return ratio > 0.05;
    }

    // ─── Full consistency test — returns both HTML outputs ──────────────────
    public record ConsistencyTestResult(
            String removedFragment,
            String originalHtml,
            String reducedHtml
    ) {}

    /**
     * Runs the SHAP consistency axiom test:
     * removes the highest-scored fragment and re-generates with the real LLM.
     */
    public ConsistencyTestResult runConsistencyTest(
            String originalPrompt,
            List<PromptFragment> fragments,
            String originalHtml,
            LlmProvider provider) {

        // Find top fragment by Shapley weight
        String topFragment = fragments.stream()
                .max(Comparator.comparingDouble(PromptFragment::weight))
                .map(PromptFragment::text)
                .orElse("");

        String reducedPrompt = originalPrompt.replace(topFragment, "").trim();

        // Generate UI for the reduced prompt using the REAL LLM
        GeneratedUi reducedUi = htmlGenerationService.generate(reducedPrompt, provider);

        return new ConsistencyTestResult(topFragment, originalHtml, reducedUi.html());
    }
}