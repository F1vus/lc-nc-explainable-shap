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
    private List<PromptFragment> fragments;
    private List<String> suggestions;
}
