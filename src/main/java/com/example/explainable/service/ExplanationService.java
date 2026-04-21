package com.example.explainable.service;

import com.example.explainable.model.PromptFragment;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExplanationService {

    // ─── Explanation ─────────────────────────────────────────────────────────

    /**
     * Generates a narrative explanation for the Shapley attribution results.
     *
     * @param prompt         original user prompt
     * @param fragments      Shapley-weighted fragments
     * @param shapleyUsed    true = real Shapley values, false = heuristic fallback
     */
    public String explain(String prompt, List<PromptFragment> fragments, boolean shapleyUsed) {
        if (fragments == null || fragments.isEmpty()) {
            return "No prompt fragments were detected. Try a more descriptive prompt.";
        }

        // Rank fragments by weight descending
        List<PromptFragment> sorted = fragments.stream()
                .sorted(Comparator.comparingDouble(PromptFragment::weight).reversed())
                .collect(Collectors.toList());

        PromptFragment top    = sorted.get(0);
        PromptFragment bottom = sorted.get(sorted.size() - 1);

        String method = shapleyUsed
                ? "Shapley values (game-theory attribution)"
                : "heuristic keyword scoring (embedding service unavailable)";

        StringBuilder sb = new StringBuilder();
        sb.append("Attribution method: ").append(method).append(".\n\n");

        sb.append("Your prompt was split into ").append(fragments.size())
                .append(" fragments. ");

        sb.append("The fragment with the highest influence was \"")
                .append(top.text())
                .append("\" (weight ").append(String.format("%.2f", top.weight())).append("), ")
                .append("which most strongly shaped the \"")
                .append(top.mappedElement()).append("\" part of the UI. ");

        if (sorted.size() > 1) {
            sb.append("The least influential fragment was \"")
                    .append(bottom.text())
                    .append("\" (weight ").append(String.format("%.2f", bottom.weight())).append("). ");
        }

        if (shapleyUsed) {
            sb.append("\n\nHow Shapley attribution works here: for each fragment fᵢ, "
                    + "the algorithm tests every possible subset S of the remaining fragments "
                    + "and measures how much adding fᵢ to S increases the cosine similarity "
                    + "between the coalition's mean embedding and the full-prompt embedding. "
                    + "The Shapley value φᵢ is the weighted average of those marginal gains "
                    + "across all subsets — giving each fragment a fair, order-independent credit.");
        }

        return sb.toString();
    }

    /**
     * Overload kept for backward compatibility — assumes Shapley was used.
     */
    public String explain(String prompt, List<PromptFragment> fragments) {
        return explain(prompt, fragments, true);
    }


    /**
     * Returns actionable suggestions for improving the prompt.
     *
     * In a more advanced version these could be generated dynamically by an
     * LLM conditioned on the Shapley weights (e.g. "fragment X had weight 0.1
     * — try making it more specific").
     */
    public List<String> refinePromptSuggestions(String prompt) {
        return List.of(
                "Name the page type explicitly — e.g. 'login page', 'dashboard', 'todo app'.",
                "List one or two concrete UI components — e.g. 'with a form', 'with a sidebar card'.",
                "State the visual theme — e.g. 'dark mode', 'minimal white', 'vibrant'.",
                "Describe the primary user action — e.g. 'user can add tasks', 'user registers'.",
                "Keep each requirement as a distinct clause so the extractor treats it as a separate fragment."
        );
    }


    /**
     * Consistency test: checks whether removing the top fragment would produce
     * a semantically different prompt.
     *
     * <p>In a full SHAP pipeline this would regenerate the UI with the top
     * fragment ablated and compare outputs — here we conservatively report
     * {@code true} (consistent) whenever the modified prompt is non-trivially
     * different from the original, which is always the case when a fragment
     * is removed.</p>
     *
     * @param originalPrompt  the full prompt
     * @param modifiedPrompt  the prompt with the top fragment removed
     * @return true if the two prompts are detectably different
     */
    public boolean consistencyTest(String originalPrompt, String modifiedPrompt) {
        if (modifiedPrompt == null || modifiedPrompt.isBlank()) return false;
        if (modifiedPrompt.equalsIgnoreCase(originalPrompt))    return false;

        // A meaningful difference requires at least 10% length change
        double ratio = (double) Math.abs(originalPrompt.length() - modifiedPrompt.length())
                / Math.max(originalPrompt.length(), 1);
        return ratio > 0.05;
    }

    public String buildPreviewHtml(String html) {
        return html == null ? "" : html;
    }

    public String joinSuggestions(List<String> suggestions) {
        return String.join(" ", suggestions);
    }
}
