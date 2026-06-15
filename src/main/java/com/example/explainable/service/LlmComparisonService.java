package com.example.explainable.service;

import com.example.explainable.client.GeminiClient;
import com.example.explainable.client.LlmClient;
import com.example.explainable.model.ComparisonResult;
import com.example.explainable.model.GeneratedUi;
import com.example.explainable.model.LlmProvider;
import com.example.explainable.model.PromptFragment;
import com.example.explainable.service.impl.LlmPromptFragmentExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmComparisonService {

    private final LlmClient groqClient;
    private final GeminiClient geminiClient;
    private final LlmPromptFragmentExtractor fragmentExtractor;
    private final ShapleyAttributionService attributionService;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private static final String HTML_GENERATION_PROMPT_TEMPLATE = """
            You are a system that generates a COMPLETE, STRUCTURED HTML/CSS user interface
            based strictly on a user prompt.

            OUTPUT FORMAT (STRICT):
            Return the response in exactly this structure:

            HTML:
            <complete valid HTML here>

            SUMMARY:
            <one short sentence describing the generated UI>

            RULES:
            - HTML must be complete: <!doctype html>, <html>, <head>, <body>
            - Include embedded CSS inside a <style> tag
            - Return only HTML in the HTML section
            - Do not add markdown fences
            - Do not add explanations outside SUMMARY
            - SUMMARY must be 1 short sentence
            - Do not invent features not supported by the prompt

            USER PROMPT:
            "%s"
            """;

    /**
     * Runs both LLMs in parallel for the same prompt and returns a ComparisonResult,
     * including per-model Shapley attribution.
     */
    public ComparisonResult compare(String prompt) {
        log.info("Starting LLM comparison for prompt: '{}'", prompt);

        String fullPrompt = HTML_GENERATION_PROMPT_TEMPLATE.formatted(prompt);

        CompletableFuture<SingleResult> groqFuture = CompletableFuture.supplyAsync(
                () -> callWithTiming("Groq", () -> groqClient.callLlm(fullPrompt)),
                executor
        );

        CompletableFuture<SingleResult> geminiFuture = CompletableFuture.supplyAsync(
                () -> callWithTiming("Gemini", () -> geminiClient.callLlm(fullPrompt)),
                executor
        );

        SingleResult groqResult = groqFuture.join();
        SingleResult geminiResult = geminiFuture.join();

        log.info("Comparison complete — Groq: {}ms, Gemini: {}ms",
                groqResult.latencyMs, geminiResult.latencyMs);

        String groqHtml = extractSection(groqResult.content, "HTML:", "SUMMARY:");
        String groqSummary = extractAfterLabel(groqResult.content, "SUMMARY:");

        String geminiHtml = extractSection(geminiResult.content, "HTML:", "SUMMARY:");
        String geminiSummary = extractAfterLabel(geminiResult.content, "SUMMARY:");

        if (groqHtml.isBlank()) groqHtml = groqResult.content;
        if (groqSummary.isBlank()) groqSummary = "Generated UI based on the provided prompt.";
        if (geminiHtml.isBlank()) geminiHtml = geminiResult.content;
        if (geminiSummary.isBlank()) geminiSummary = "Generated UI based on the provided prompt.";

        // Compute Shapley attribution per model
        List<String> fragments = extractFragmentsSafely(prompt);
        List<PromptFragment> groqFragments = computeAttributionSafely(prompt, fragments,
                new GeneratedUi(groqHtml, groqSummary, ""), groqResult.success);
        List<PromptFragment> geminiFragments = computeAttributionSafely(prompt, fragments,
                new GeneratedUi(geminiHtml, geminiSummary, ""), geminiResult.success);

        return ComparisonResult.builder()
                .prompt(prompt)
                .groqHtml(groqHtml)
                .groqSummary(groqSummary)
                .groqExplanation("")
                .groqLatencyMs(groqResult.latencyMs)
                .groqSuccess(groqResult.success)
                .groqError(groqResult.error)
                .groqFragments(groqFragments)
                .geminiHtml(geminiHtml)
                .geminiSummary(geminiSummary)
                .geminiExplanation("")
                .geminiLatencyMs(geminiResult.latencyMs)
                .geminiSuccess(geminiResult.success)
                .geminiError(geminiResult.error)
                .geminiFragments(geminiFragments)
                .providerAName(LlmProvider.GROQ.getDisplayName())
                .providerBName(LlmProvider.GEMINI.getDisplayName())
                .modelAId(LlmProvider.GROQ.getModelId())
                .modelBId(LlmProvider.GEMINI.getModelId())
                .build();
    }

    private List<String> extractFragmentsSafely(String prompt) {
        try {
            return fragmentExtractor.extract(prompt);
        } catch (Exception e) {
            log.warn("Fragment extraction failed, skipping SHAP: {}", e.getMessage());
            return List.of();
        }
    }

    private List<PromptFragment> computeAttributionSafely(String prompt, List<String> fragments,
                                                          GeneratedUi ui, boolean modelSuccess) {
        if (!modelSuccess || fragments.isEmpty()) return List.of();
        try {
            return attributionService.computeShapleyAttribution(prompt, fragments, ui);
        } catch (Exception e) {
            log.warn("Shapley attribution failed for model: {}", e.getMessage());
            return List.of();
        }
    }

    private SingleResult callWithTiming(String name, java.util.function.Supplier<String> call) {
        long start = System.currentTimeMillis();
        try {
            String content = call.get();
            long ms = System.currentTimeMillis() - start;
            log.info("{} completed in {} ms", name, ms);
            return new SingleResult(content, ms, true, null);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            log.error("{} failed after {} ms: {}", name, ms, e.getMessage());
            return new SingleResult("", ms, false, e.getMessage());
        }
    }

    private String extractSection(String text, String startLabel, String endLabel) {
        int start = text.indexOf(startLabel);
        if (start < 0) return "";
        start += startLabel.length();
        int end = text.indexOf(endLabel, start);
        if (end < 0) return text.substring(start).trim();
        return text.substring(start, end).trim();
    }

    private String extractAfterLabel(String text, String label) {
        int start = text.indexOf(label);
        if (start < 0) return "";
        return text.substring(start + label.length()).trim();
    }

    private record SingleResult(String content, long latencyMs, boolean success, String error) {}
}