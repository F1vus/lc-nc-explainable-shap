package com.example.explainable.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GenerationResult {
    private String prompt;
    private String html;
    private String css;
    private String previewHtml;
    private String explanation;
    private boolean consistent;
    private boolean shapleyUsed;
    private List<PromptFragment> fragments;
    private List<String> suggestions;
    private LlmProvider llmProvider;

    // Consistency test
    private String consistencyRemovedFragment;
    private String consistencyOriginalHtml;
    private String consistencyReducedHtml;
}