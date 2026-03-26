package com.example.explainable.service;

import com.example.explainable.model.PromptFragment;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class ExplanationService {

    public String explain(String prompt, List<PromptFragment> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return "No prompt fragments detected.";
        }

        PromptFragment top = fragments.stream()
                .max(Comparator.comparingDouble(PromptFragment::weight))
                .orElse(fragments.getFirst());

        return "The system detected " + fragments.size() + " relevant fragments. "
                + "The strongest one was '" + top.text() + "' with weight " + String.format("%.2f", top.weight())
                + ", which influenced the '" + top.mappedElement() + "' part of the generated interface. "
                + "This is SHAP-inspired attribution, not exact SHAP.";
    }

    public List<String> refinePromptSuggestions(String prompt) {
        return List.of(
                "Add the target page type, e.g. login page, dashboard, todo app.",
                "Specify one or two UI components, e.g. button, form, card, table.",
                "Mention theme preferences, e.g. dark mode or minimal style.",
                "Add expected user action, e.g. register, search, add item."
        );
    }

    public boolean consistencyTest(String originalPrompt, String modifiedPrompt) {
        return modifiedPrompt != null && !modifiedPrompt.isBlank() && !modifiedPrompt.equalsIgnoreCase(originalPrompt);
    }

    public String buildPreviewHtml(String html) {
        return html == null ? "" : html;
    }

    public String joinSuggestions(List<String> suggestions) {
        return String.join("", suggestions);
    }
}
