package com.example.explainable.controller;

import com.example.explainable.model.GenerationRequest;
import com.example.explainable.model.GenerationResult;
import com.example.explainable.service.AttributionService;
import com.example.explainable.service.ExplanationService;
import com.example.explainable.service.HtmlGenerationService;
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
    private final HtmlGenerationService HtmlGenerationService;
    private final AttributionService attributionService;
    private final ExplanationService explanationService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("generationRequest", new GenerationRequest());
        return "index";
    }

    @PostMapping("/generate")
    public String generate(@Valid GenerationRequest generationRequest, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "index";
        }

        String prompt = generationRequest.getPrompt();

        List<String> fragments = extractor.extract(prompt);
        log.info("Fragments: {}", fragments);
        var ui = HtmlGenerationService.generate(prompt);
        var attributed = attributionService.attribute(prompt, fragments);

        GenerationResult result = new GenerationResult();
        result.setPrompt(prompt);
        result.setHtml(ui.htmlAndCss());
        result.setCss("css{" +
                "background-color: #fff;" +
                "text-align: center;" +
                "display: inline-block;" +
                "color: #fff;}");
        result.setPreviewHtml(ui.htmlAndCss());
        result.setFragments(attributed);
        result.setExplanation(explanationService.explain(prompt, attributed));
        result.setSuggestions(explanationService.refinePromptSuggestions(prompt));
        result.setConsistent(explanationService.consistencyTest(prompt, prompt + " with more detail"));

        model.addAttribute("result", result);
        model.addAttribute("generationRequest", generationRequest);
        return "result";
    }
}
