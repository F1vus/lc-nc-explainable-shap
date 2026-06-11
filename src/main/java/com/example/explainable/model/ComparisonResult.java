package com.example.explainable.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ComparisonResult {

    private final String prompt;

    // Groq / LLM-A result
    private final String groqHtml;
    private final String groqSummary;
    private final String groqExplanation;
    private final long groqLatencyMs;
    private final boolean groqSuccess;
    private final String groqError;

    // Gemini / LLM-B result
    private final String geminiHtml;
    private final String geminiSummary;
    private final String geminiExplanation;
    private final long geminiLatencyMs;
    private final boolean geminiSuccess;
    private final String geminiError;

    // Provider display names
    private final String providerAName;
    private final String providerBName;
    private final String modelAId;
    private final String modelBId;
}