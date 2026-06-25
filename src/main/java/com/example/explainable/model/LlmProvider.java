package com.example.explainable.model;

public enum LlmProvider {
    GROQ("Groq (Llama 3.3 70B)", "llama-3.3-70b-versatile"),
    GEMINI("Google Gemini 2.0 Flash", "gemini-2.5-flash");

    private final String displayName;
    private final String modelId;

    LlmProvider(String displayName, String modelId) {
        this.displayName = displayName;
        this.modelId = modelId;
    }

    public String getDisplayName() { return displayName; }
    public String getModelId() { return modelId; }
}