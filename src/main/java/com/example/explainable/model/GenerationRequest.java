package com.example.explainable.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerationRequest {
    @NotBlank(message = "Prompt cannot be empty")
    private String prompt;
}
