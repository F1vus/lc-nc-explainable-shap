package com.example.explainable.controller;

import com.example.explainable.model.GenerationRequest;
import com.example.explainable.model.GenerationResult;
import com.example.explainable.model.PromptFragment;
import com.example.explainable.service.AttributionService;
import com.example.explainable.service.ExplanationService;
import com.example.explainable.service.HtmlGenerationService;
import com.example.explainable.service.LlmHtmlGenerationService;
import com.example.explainable.service.impl.HeuristicPromptFragmentExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController {

    private final HeuristicPromptFragmentExtractor extractor;
    private final LlmHtmlGenerationService htmlGenerationService;
    private final AttributionService attributionService;
    private final ExplanationService explanationService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("generationRequest", new GenerationRequest());
        return "index";
    }

    @PostMapping("/generate")
    public String generate(
            @Valid GenerationRequest generationRequest,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            return "index";
        }

        String prompt = generationRequest.getPrompt();
        log.info("Processing prompt: '{}'", prompt);

        // ── Step 1: Extract fragments ─────────────────────────────────────
        List<String> rawFragments = extractor.extract(prompt);
        log.info("Extracted {} fragments: {}", rawFragments.size(), rawFragments);

        // ── Step 2: Generate HTML via LLM ────────────────────────────────
        var ui = htmlGenerationService.generate(prompt);
        log.info("HTML generated, title='{}'", ui.title());

        // ── Step 3: Compute Shapley attribution (with heuristic fallback) ─
        AttributionService.AttributionResult attribution = attributionService.attribute(prompt, rawFragments);
        List<PromptFragment> attributedFragments = attribution.fragments();
        boolean shapleyUsed = attribution.shapleyUsed();
        log.info("Attribution complete: shapleyUsed={}", shapleyUsed);

        // ── Step 4: Build consistency test — ablate the top fragment ──────
        // We remove the highest-weight fragment and compare the resulting
        // truncated prompt against the original.  A real implementation would
        // regenerate the UI and compare outputs; here we test prompt-level change.
        String topFragment = attributedFragments.isEmpty() ? "" :
                attributedFragments.stream()
                .max(java.util.Comparator.comparingDouble(PromptFragment::weight))
                .map(PromptFragment::text)
                .orElse("");
        String ablatedPrompt = prompt.replace(topFragment, "").trim();
        boolean consistent   = explanationService.consistencyTest(prompt, ablatedPrompt);

        // ── Step 5: Assemble result model ─────────────────────────────────
        GenerationResult result = new GenerationResult();
        result.setPrompt(prompt);
        result.setHtml(ui.htmlAndCss());
        result.setPreviewHtml(ui.htmlAndCss());
        result.setFragments(attributedFragments);
        result.setExplanation(explanationService.explain(prompt, attributedFragments, shapleyUsed));
        result.setSuggestions(explanationService.refinePromptSuggestions(prompt));
        result.setConsistent(consistent);
        // Expose the attribution method flag so result.html can show a badge
        result.setShapleyUsed(shapleyUsed);

        model.addAttribute("result", result);
        model.addAttribute("generationRequest", generationRequest);
        log.info("Rendering result page");
        return "result";
    }
}