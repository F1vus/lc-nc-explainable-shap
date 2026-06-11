package com.example.explainable.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComparisonRequest {

    @NotBlank(message = "Prompt cannot be empty")
    private String prompt;
}