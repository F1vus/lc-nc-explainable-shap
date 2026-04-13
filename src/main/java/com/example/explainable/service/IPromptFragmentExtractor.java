package com.example.explainable.service;

import java.util.List;

public interface IPromptFragmentExtractor {
    List<String> extract(String prompt);
}
