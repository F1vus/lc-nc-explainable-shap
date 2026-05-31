package com.example.explainable.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HeuristicPromptFragmentExtractor – consistency tests")
class HeuristicPromptFragmentExtractorTest {

    private final HeuristicPromptFragmentExtractor extractor = new HeuristicPromptFragmentExtractor();

    // ── Stop-word list mirrors the production implementation ──────────────
    private static final List<String> STOP_WORDS = List.of(
            "a", "an", "and", "or", "the", "to", "of", "for",
            "with", "in", "on", "at", "by", "from", "is", "are",
            "be", "as", "it", "this", "that", "into", "using", "use"
    );

    // 1. Null / blank input
    @Nested
    @DisplayName("Null and blank inputs return an empty list")
    class NullBlankTests {

        @Test
        @DisplayName("null input → empty list")
        void nullInput_returnsEmpty() {
            assertThat(extractor.extract(null)).isEmpty();
        }

        @ParameterizedTest(name = "blank=''{0}'' → empty list")
        @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
        @DisplayName("Blank input → empty list")
        void blankInput_returnsEmpty(String blank) {
            assertThat(extractor.extract(blank)).isEmpty();
        }
    }

    // 2. Maximum 8 fragments
    @Test
    @DisplayName("Very long prompt is capped at 8 fragments")
    void longPrompt_cappedAtEight() {
        // 20 distinct meaningful tokens, none are stop-words
        String prompt = "alpha beta gamma delta epsilon zeta eta theta iota kappa " +
                        "lambda mu nu xi omicron pi rho sigma tau upsilon";
        List<String> fragments = extractor.extract(prompt);
        assertThat(fragments).hasSizeLessThanOrEqualTo(8);
    }

    // 3. No standalone stop-words in output
    @Test
    @DisplayName("Stop-words never appear as the first token of a fragment")
    void stopWordsNotLeadingFragment() {
        String prompt = "create a login page with dark mode and sidebar";
        List<String> fragments = extractor.extract(prompt);

        fragments.forEach(fragment -> {
            String firstToken = fragment.split("\\s+")[0].trim();
            assertThat(STOP_WORDS)
                    .as("Stop-word '%s' should not be the first token in fragment '%s'",
                            firstToken, fragment)
                    .doesNotContain(firstToken);
        });
    }

    // 4. No duplicates
    @Test
    @DisplayName("Output fragments are distinct (no duplicates)")
    void outputIsDistinct() {
        String prompt = "login login login login page page";
        List<String> fragments = extractor.extract(prompt);
        assertThat(fragments).doesNotHaveDuplicates();
    }

    // 5. No blank fragments
    @Test
    @DisplayName("No fragment in the output is blank or empty")
    void noBlankFragment() {
        String prompt = "a simple dark mode dashboard with card components";
        List<String> fragments = extractor.extract(prompt);
        fragments.forEach(f ->
                assertThat(f).as("Fragment must not be blank").isNotBlank()
        );
    }

    // 6. Idempotency – same result for repeated calls
    @Test
    @DisplayName("Calling extract twice with the same prompt yields identical results")
    void idempotent() {
        String prompt = "Create a todo list app with dark mode";
        assertThat(extractor.extract(prompt))
                .isEqualTo(extractor.extract(prompt));
    }

    // 7. Meaningful typical cases
    @Test
    @DisplayName("Single meaningful token returns exactly one fragment")
    void singleToken_returnsOneFragment() {
        List<String> fragments = extractor.extract("dashboard");
        assertThat(fragments).hasSize(1).containsExactly("dashboard");
    }

    @Test
    @DisplayName("Only stop-words → empty result")
    void onlyStopWords_returnsEmpty() {
        // All tokens are stop-words — nothing survives the filter
        String prompt = "a and or the to of";
        List<String> fragments = extractor.extract(prompt);
        assertThat(fragments).isEmpty();
    }

    @Test
    @DisplayName("Typical LC/NC prompt yields at least 2 and at most 8 fragments")
    void typicalPrompt_reasonableFragmentCount() {
        String prompt = "Create a login page with dark mode and sidebar navigation";
        List<String> fragments = extractor.extract(prompt);
        assertThat(fragments)
                .hasSizeGreaterThanOrEqualTo(2)
                .hasSizeLessThanOrEqualTo(8);
    }
}
