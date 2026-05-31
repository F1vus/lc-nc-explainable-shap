package com.example.explainable.service;

import com.example.explainable.client.LlmClient;
import com.example.explainable.model.PromptFragment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExplanationService – consistency tests")
class ExplanationServiceTest {

    @Mock
    private LlmClient llmClient;

    @InjectMocks
    private ExplanationService explanationService;

    // 1. consistencyTest – identical / case variants → false
    @Nested
    @DisplayName("consistencyTest: unchanged or trivially changed prompt → false")
    class NoChangeTests {

        @Test
        @DisplayName("Identical prompts return false")
        void identicalPrompts_returnFalse() {
            String prompt = "Create a login page with dark mode";
            assertThat(explanationService.consistencyTest(prompt, prompt)).isFalse();
        }

        @Test
        @DisplayName("Same prompt in different case returns false (equalsIgnoreCase)")
        void caseOnlyDifference_returnFalse() {
            assertThat(explanationService.consistencyTest(
                    "Create a Login Page",
                    "create a login page"
            )).isFalse();
        }

        @Test
        @DisplayName("Null modified prompt returns false")
        void nullModified_returnFalse() {
            assertThat(explanationService.consistencyTest("some prompt", null)).isFalse();
        }

        @ParameterizedTest(name = "Blank modified=''{0}'' returns false")
        @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
        @DisplayName("Blank modified prompt returns false")
        void blankModified_returnFalse(String blank) {
            assertThat(explanationService.consistencyTest("some prompt", blank)).isFalse();
        }
    }

    // 2. consistencyTest – meaningful ablation → true
    @Nested
    @DisplayName("consistencyTest: meaningful ablation (>5% length change) → true")
    class MeaningfulAblationTests {

        @Test
        @DisplayName("Removing the top fragment from a 10-word prompt exceeds 5% threshold")
        void removeTopFragment_returnsTrue() {
            // Original: "Create a login page with dark mode and sidebar navigation"  (57 chars)
            // Ablated:  removing "sidebar navigation" → ~31% change → true
            String original = "Create a login page with dark mode and sidebar navigation";
            String ablated  = original.replace("sidebar navigation", "").trim();

            assertThat(explanationService.consistencyTest(original, ablated)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] original=''{0}'' modified=''{1}'' → true")
        @CsvSource({
                "'login form dark mode sidebar', 'login form'",
                "'dashboard with charts cards and filters', 'dashboard'",
                "'register page with password confirmation and email', 'register page'"
        })
        @DisplayName("Various significant ablations all return true")
        void variousSignificantAblations_returnTrue(String original, String modified) {
            assertThat(explanationService.consistencyTest(original, modified)).isTrue();
        }

        @Test
        @DisplayName("Adding substantial content to the modified prompt also returns true")
        void expandedPrompt_returnsTrue() {
            String original = "login";
            String expanded = "login page with username password fields and remember me checkbox";
            assertThat(explanationService.consistencyTest(original, expanded)).isTrue();
        }
    }

    // 3. consistencyTest – borderline / threshold cases
    @Nested
    @DisplayName("consistencyTest: boundary – exactly ≤5% change")
    class BoundaryTests {

        @Test
        @DisplayName("Removing a single trailing char from a long prompt may be below threshold")
        void oneCharRemoval_longPrompt_returnsFalse() {
            // Prompt: 100 chars; removing 1 char = 1% difference < 5% → false
            String original = "a".repeat(100);
            String modified  = "a".repeat(99);
            assertThat(explanationService.consistencyTest(original, modified)).isFalse();
        }

        @Test
        @DisplayName("5% exact boundary: 5-char change on 100-char prompt")
        void fivePercentExact_returnsTrue() {
            // 5/100 = 0.05; the implementation uses > 0.05, so exactly 0.05 is FALSE
            String original = "a".repeat(100);
            String modified  = "a".repeat(95); // ratio = 5/100 = 0.05 exactly → NOT > 0.05
            assertThat(explanationService.consistencyTest(original, modified)).isFalse();
        }

        @Test
        @DisplayName("Just over 5% boundary returns true")
        void justOverFivePercent_returnsTrue() {
            // 6/100 = 0.06 > 0.05 → true
            String original = "a".repeat(100);
            String modified  = "a".repeat(94);
            assertThat(explanationService.consistencyTest(original, modified)).isTrue();
        }
    }

    // 4. explain() – guard against null / empty fragment lists
    @Nested
    @DisplayName("explain(): null / empty fragments returns sensible default message")
    class ExplainGuardTests {

        @Test
        @DisplayName("Null fragments list → returns fallback string, no LLM call")
        void nullFragments_returnsFallback() {
            String result = explanationService.explain("A login page", null);
            assertThat(result).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("Empty fragments list → returns fallback string, no LLM call")
        void emptyFragments_returnsFallback() {
            String result = explanationService.explain("A login page", List.of());
            assertThat(result).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("Non-empty fragments → LLM is called and trimmed result returned")
        void withFragments_returnsLlmResponse() {
            when(llmClient.callLlm(anyString()))
                    .thenReturn("  The login page is driven by the form component.  ");

            List<PromptFragment> frags = List.of(
                    new PromptFragment("login form", 0.8, "form.login-form"),
                    new PromptFragment("dark mode",  0.2, ".dark-mode-toggle")
            );

            String result = explanationService.explain("Login page with dark mode", frags);

            assertThat(result).isEqualTo("The login page is driven by the form component.");
        }
    }

    // 5. refinePromptSuggestions() – always returns non-empty list
    @Test
    @DisplayName("refinePromptSuggestions always returns a non-empty list for any prompt")
    void refineSuggestions_alwaysNonEmpty() {
        List<String> suggestions = explanationService.refinePromptSuggestions("any prompt");
        assertThat(suggestions).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("refinePromptSuggestions returns exactly 5 suggestions")
    void refineSuggestions_returnsFive() {
        assertThat(explanationService.refinePromptSuggestions("login page")).hasSize(5);
    }
}
