package com.example.explainable.service.heuristic;

import com.example.explainable.model.PromptFragment;
import com.example.explainable.service.ShapleyAttributionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttributionService {

    private final ShapleyAttributionService shapleyService;

    /**
     * Attribute each fragment with a weight.
     *
     * @return fragments annotated with Shapley weights (or heuristic weights
     *         if the embedding service is unavailable)
     */
    public AttributionResult attribute(String prompt, List<String> fragments) {
            List<PromptFragment> attributed = heuristicAttribution(prompt, fragments);
            return new AttributionResult(attributed, false);
    }

    // ─── Heuristic fallback ───────────────────────────────────────────────────

    /**
     * Keyword-based attribution used as a fallback.
     * Scoring rules (in order of precedence):
     *   1. Fragment appears verbatim in the lower-cased prompt → 0.90
     *   2. Fragment contains a known high-signal UI keyword     → 0.75–0.95
     *   3. Fragment is very short (≤ 3 chars)                   → 0.20
     *   4. Default                                              → 0.45
     * NOTE: These are *heuristic* approximations, not game-theoretic values.
     */
    private List<PromptFragment> heuristicAttribution(String prompt, List<String> fragments) {
        List<PromptFragment> result = new ArrayList<>();
        String lower = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);

        for (String fragment : fragments) {
            double weight = heuristicScore(fragment, lower);
            result.add(new PromptFragment(fragment, weight, mapElement(fragment)));
        }
        return result;
    }

    private double heuristicScore(String fragment, String lowerPrompt) {
        if (fragment == null || fragment.isBlank()) return 0.0;
        String f = fragment.toLowerCase(Locale.ROOT).trim();

        if (lowerPrompt.contains(f))                          return 0.90;
        if (f.contains("todo") || f.contains("task"))         return 0.95;
        if (f.contains("login") || f.contains("sign"))        return 0.85;
        if (f.contains("dark"))                               return 0.80;
        if (f.contains("dashboard"))                          return 0.75;
        if (f.length() <= 3)                                  return 0.20;
        return 0.45;
    }

    private String mapElement(String fragment) {
        String f = fragment.toLowerCase(Locale.ROOT);
        if (f.contains("login")  || f.contains("sign"))      return "form";
        if (f.contains("dark")   || f.contains("theme"))     return "theme";
        if (f.contains("todo")   || f.contains("task"))      return "task list";
        if (f.contains("button") || f.contains("action"))    return "button";
        if (f.contains("card"))                               return "card";
        if (f.contains("input")  || f.contains("field"))     return "input";
        if (f.contains("nav")    || f.contains("menu"))      return "navigation";
        if (f.contains("dashboard"))                          return "dashboard layout";
        return "layout";
    }

    /**
     * Carries both the attributed fragments and a flag indicating whether
     * real Shapley values ({@code true}) or heuristic scores ({@code false})
     * were used.  The controller uses this flag to display a warning badge.
     */

    public record AttributionResult(List<PromptFragment> fragments, boolean shapleyUsed) {}
}