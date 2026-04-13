package com.example.explainable.service.impl;

import com.example.explainable.service.IPromptFragmentExtractor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HeuristicPromptFragmentExtractor implements IPromptFragmentExtractor {

    private static final Set<String> STOP_WORDS = Set.of(
            "a","an","and","or","the","to","of","for","with","in","on","at","by","from","is","are","be","as","it","this","that","into","using","use"
    );

    @Override
    public List<String> extract(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return List.of();
        }

        String normalized = prompt.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ąćęłńóśźż\\s-]", " ");
        String[] tokens = normalized.trim().split("\\s+");

        List<String> fragments = new ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.isBlank() || STOP_WORDS.contains(token)) {
                continue;
            }

            if (i + 1 < tokens.length && !STOP_WORDS.contains(tokens[i + 1])) {
                fragments.add(token + " " + tokens[i + 1]);
            } else {
                fragments.add(token);
            }
        }

        return fragments.stream()
                .distinct()
                .limit(8)
                .collect(Collectors.toList());
    }
}
