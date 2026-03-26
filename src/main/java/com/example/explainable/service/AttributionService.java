package com.example.explainable.service;

import com.example.explainable.model.PromptFragment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AttributionService {

    public List<PromptFragment> attribute(String prompt, List<String> fragments) {
        List<PromptFragment> result = new ArrayList<>();
        String lower = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);

        for (String fragment : fragments) {
            double weight = score(fragment, lower);
            result.add(new PromptFragment(fragment, weight, mapElement(fragment)));
        }
        return result;
    }

    private double score(String fragment, String prompt) {
        if (fragment == null || fragment.isBlank()) {
            return 0.0;
        }
        String f = fragment.toLowerCase(Locale.ROOT).trim();
        if (prompt.contains(f)) return 0.9;
        if (f.contains("login")) return 0.85;
        if (f.contains("dark")) return 0.8;
        if (f.contains("todo") || f.contains("task")) return 0.95;
        if (f.contains("dashboard")) return 0.75;
        if (f.length() <= 3) return 0.2;
        return 0.45;
    }

    private String mapElement(String fragment) {
        String f = fragment.toLowerCase(Locale.ROOT);
        if (f.contains("login") || f.contains("sign")) return "form";
        if (f.contains("dark")) return "theme";
        if (f.contains("todo") || f.contains("task")) return "task list";
        if (f.contains("button") || f.contains("action")) return "button";
        if (f.contains("card")) return "card";
        if (f.contains("input") || f.contains("field")) return "input";
        return "layout";
    }
}
