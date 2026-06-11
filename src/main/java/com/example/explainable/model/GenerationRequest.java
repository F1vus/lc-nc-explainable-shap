package com.example.explainable.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerationRequest {
    @NotBlank(message = "Prompt cannot be empty")
    private String prompt;

    @NotNull(message = "LLM provider must be selected")
    private LlmProvider llmProvider = LlmProvider.GROQ;
}
