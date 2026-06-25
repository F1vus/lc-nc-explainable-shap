package com.example.explainable.controller;

import com.example.explainable.model.*;
import com.example.explainable.service.*;
import com.example.explainable.service.impl.LlmPromptFragmentExtractor;
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

    private final LlmPromptFragmentExtractor extractor;
    private final LlmHtmlGenerationService htmlGenerationService;
    private final ShapleyAttributionService attributionService;
    private final ExplanationService explanationService;
    private final LlmComparisonService comparisonService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("generationRequest", new GenerationRequest());
        model.addAttribute("comparisonRequest", new ComparisonRequest());
        model.addAttribute("llmProviders", LlmProvider.values());
        return "index";
    }

    @PostMapping("/generate")
    public String generate(
            @Valid GenerationRequest generationRequest,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("comparisonRequest", new ComparisonRequest());
            model.addAttribute("llmProviders", LlmProvider.values());
            return "index";
        }

        String prompt = generationRequest.getPrompt();
        LlmProvider provider = generationRequest.getLlmProvider() != null
                ? generationRequest.getLlmProvider()
                : LlmProvider.GROQ;

        log.info("Processing prompt: '{}' with provider={}", prompt, provider);

        List<String> rawFragments = extractor.extract(prompt);
        log.info("Extracted {} fragments: {}", rawFragments.size(), rawFragments);

        var ui = htmlGenerationService.generate(prompt, provider);
        log.info("HTML generated, title='{}', provider={}", ui.title(), provider);

        List<PromptFragment> attributedFragments = attributionService.computeShapleyAttribution(prompt, rawFragments, ui);
        boolean shapleyUsed = true;
        log.info("Attribution complete: shapleyUsed={}", shapleyUsed);

        String topFragment = attributedFragments.isEmpty() ? "" :
                attributedFragments.stream()
                        .max(java.util.Comparator.comparingDouble(PromptFragment::weight))
                        .map(PromptFragment::text)
                        .orElse("");
        String ablatedPrompt = prompt.replace(topFragment, "").trim();

        String explanation = explanationService.explain(ui.summary(), attributedFragments);
        boolean consistent = explanationService.consistencyTest(prompt, ablatedPrompt);

        // ── Run the full consistency test with real LLM ──────────────────
        ExplanationService.ConsistencyTestResult consistencyResult =
                explanationService.runConsistencyTest(prompt, attributedFragments, ui.html(), provider);
        log.info("Consistency test done: removed='{}', reducedHtmlLength={}",
                consistencyResult.removedFragment(), consistencyResult.reducedHtml().length());

        GenerationResult result = new GenerationResult();
        result.setPrompt(prompt);
        result.setHtml(ui.html());
        result.setPreviewHtml(ui.html());
        result.setFragments(attributedFragments);
        result.setExplanation(explanation);
        result.setSuggestions(explanationService.refinePromptSuggestions(prompt));
        result.setConsistent(consistent);
        result.setShapleyUsed(shapleyUsed);
        result.setLlmProvider(provider);

        result.setConsistencyRemovedFragment(consistencyResult.removedFragment());
        result.setConsistencyOriginalHtml(consistencyResult.originalHtml());
        result.setConsistencyReducedHtml(consistencyResult.reducedHtml());
        result.setConsistencyReducedFragments(consistencyResult.reducedFragments());

        model.addAttribute("result", result);
        model.addAttribute("generationRequest", generationRequest);
        log.info("Rendering result page");
        return "result";
    }

    // ── LLM Comparison ─────────────────────────────────────────────────────

    @GetMapping("/compare")
    public String compareForm(Model model) {
        model.addAttribute("generationRequest", new GenerationRequest());
        model.addAttribute("comparisonRequest", new ComparisonRequest());
        return "index";
    }

    @PostMapping("/compare")
    public String compare(
            @Valid ComparisonRequest comparisonRequest,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("generationRequest", new GenerationRequest());
            return "index";
        }

        String prompt = comparisonRequest.getPrompt();
        log.info("Running LLM comparison for prompt: '{}'", prompt);

        ComparisonResult result = comparisonService.compare(prompt);

        model.addAttribute("comparison", result);
        model.addAttribute("comparisonRequest", comparisonRequest);
        log.info("Rendering comparison result page");
        return "compare";
    }

    @GetMapping("/error")
    public String error(Model model) {
        return "error";
    }

}