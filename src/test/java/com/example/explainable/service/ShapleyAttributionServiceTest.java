package com.example.explainable.service;

import com.example.explainable.client.EmbeddingClient;
import com.example.explainable.model.GeneratedUi;
import com.example.explainable.model.PromptFragment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShapleyAttributionService – consistency invariants")
class ShapleyAttributionServiceTest {

    // ── tolerance for floating-point comparisons ─────────────────────────────
    private static final double EPS = 1e-6;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private LlmFragmentMappingService fragmentMappingService;

    @Mock
    private LlmHtmlElementExtractor htmlElementExtractor;

    @InjectMocks
    private ShapleyAttributionService service;

    // ── Shared stub HTML / GeneratedUi ──────────────────────────────────────
    private static final GeneratedUi STUB_UI = new GeneratedUi(
            "<html><body><form class='login-form'><button>Submit</button></form></body></html>",
            "A simple login form",
            "Login UI"
    );

    @BeforeEach
    void stubCommonMocks() {
        org.mockito.Mockito.lenient()
                .when(fragmentMappingService.mapToUiElement(anyString(), anyString()))
                .thenReturn("form.login-form");

        org.mockito.Mockito.lenient()
                .when(htmlElementExtractor.extract(anyString()))
                .thenReturn(List.of("form.login-form", "button"));

        org.mockito.Mockito.lenient()
                .when(embeddingClient.embed("form.login-form"))
                .thenReturn(new double[]{1.0, 0.0, 0.0, 0.0});

        org.mockito.Mockito.lenient()
                .when(embeddingClient.embed("button"))
                .thenReturn(new double[]{0.0, 1.0, 0.0, 0.0});

        org.mockito.Mockito.lenient()
                .when(embeddingClient.embed("body"))
                .thenReturn(new double[]{0.0, 0.0, 0.0, 1.0});
    }

    // 1. Efficiency invariant – weights must sum to 1.0 (softmax)
    @Nested
    @DisplayName("Efficiency: weights sum to 1.0")
    class EfficiencyTests {

        @Test
        @DisplayName("Two orthogonal fragments → weights sum to exactly 1.0")
        void twoFragments_weightsSumToOne() {
            stubEmbedding("login form", unitVector(new double[]{1, 0, 0, 0}));
            stubEmbedding("dark mode",  unitVector(new double[]{0, 1, 0, 0}));

            List<PromptFragment> result = service.computeShapleyAttribution(
                    "login form dark mode",
                    List.of("login form", "dark mode"),
                    STUB_UI
            );

            double sum = result.stream().mapToDouble(PromptFragment::weight).sum();
            assertThat(sum).isCloseTo(1.0, within(EPS));
        }

        @Test
        @DisplayName("Three fragments → weights sum to exactly 1.0")
        void threeFragments_weightsSumToOne() {
            String[] fragments = {"login form", "dark theme", "sidebar nav"};
            stubEmbedding("login form",  unitVector(new double[]{1, 0, 0, 0}));
            stubEmbedding("dark theme",  unitVector(new double[]{0, 1, 0, 0}));
            stubEmbedding("sidebar nav", unitVector(new double[]{0, 0, 1, 0}));

            List<PromptFragment> result = service.computeShapleyAttribution(
                    "login form dark theme sidebar nav",
                    List.of(fragments),
                    STUB_UI
            );

            double sum = result.stream().mapToDouble(PromptFragment::weight).sum();
            assertThat(sum).isCloseTo(1.0, within(EPS));
        }

        @Test
        @DisplayName("Single fragment → weight equals 1.0")
        void singleFragment_weightIsOne() {
            stubEmbedding("login form", unitVector(new double[]{1, 0, 0, 0}));
            // Nadpisujemy ekstraktor dla 1 fragmentu, aby uniknąć IndexOutOfBoundsException
            when(htmlElementExtractor.extract(anyString())).thenReturn(List.of("form.login-form"));

            List<PromptFragment> result = service.computeShapleyAttribution(
                    "login form",
                    List.of("login form"),
                    STUB_UI
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).weight()).isCloseTo(1.0, within(EPS));
        }
    }

    // 2. Non-negativity invariant – every weight ∈ [0, 1]
    @Nested
    @DisplayName("Non-negativity: all weights in [0, 1]")
    class NonNegativityTests {

        @Test
        @DisplayName("Orthogonal fragments all get positive weights")
        void orthogonalFragments_allWeightsPositive() {
            stubEmbedding("header",   unitVector(new double[]{1, 0, 0, 0}));
            stubEmbedding("footer",   unitVector(new double[]{0, 1, 0, 0}));
            stubEmbedding("sidebar",  unitVector(new double[]{0, 0, 1, 0}));

            List<PromptFragment> result = service.computeShapleyAttribution(
                    "header footer sidebar",
                    List.of("header", "footer", "sidebar"),
                    STUB_UI
            );

            result.forEach(f -> {
                assertThat(f.weight())
                        .as("Weight for fragment '%s' must be >= 0", f.text())
                        .isGreaterThanOrEqualTo(0.0);
                assertThat(f.weight())
                        .as("Weight for fragment '%s' must be <= 1", f.text())
                        .isLessThanOrEqualTo(1.0 + EPS);
            });
        }

        @Test
        @DisplayName("Fragments pointing away from target still get non-negative softmax weight")
        void antiCorrelatedFragment_stillNonNegative() {
            stubEmbedding("irrelevant part", unitVector(new double[]{-1, 0, 0, 0}));
            stubEmbedding("login form",      unitVector(new double[]{ 1, 0, 0, 0}));

            List<PromptFragment> result = service.computeShapleyAttribution(
                    "login form irrelevant part",
                    List.of("login form", "irrelevant part"),
                    STUB_UI
            );

            result.forEach(f ->
                    assertThat(f.weight())
                            .as("Softmax must keep weight >= 0 for '%s'", f.text())
                            .isGreaterThanOrEqualTo(0.0)
            );
        }
    }

    // 3. Symmetry invariant – order of fragments must not affect their weights
    @Nested
    @DisplayName("Symmetry: permuting input order does not change individual weights")
    class SymmetryTests {

        @Test
        @DisplayName("Reversed fragment list produces the same weights (by text)")
        void reversedOrder_sameWeights() {
            double[] embA = unitVector(new double[]{1, 0, 0, 0});
            double[] embB = unitVector(new double[]{0, 1, 0, 0});

            stubEmbedding("alpha", embA);
            stubEmbedding("beta",  embB);

            String prompt = "alpha beta";

            List<PromptFragment> fwd = service.computeShapleyAttribution(
                    prompt, List.of("alpha", "beta"), STUB_UI);

            List<PromptFragment> rev = service.computeShapleyAttribution(
                    prompt, List.of("beta", "alpha"), STUB_UI);

            double weightAlphaFwd = findWeight(fwd, "alpha");
            double weightAlphaRev = findWeight(rev, "alpha");
            double weightBetaFwd  = findWeight(fwd, "beta");
            double weightBetaRev  = findWeight(rev, "beta");

            assertThat(weightAlphaFwd).isCloseTo(weightAlphaRev, within(EPS));
            assertThat(weightBetaFwd) .isCloseTo(weightBetaRev,  within(EPS));
        }
    }

    // 4. Dummy player – zero-norm embedding contributes nothing
    @Nested
    @DisplayName("Dummy player: near-zero embedding gets the lowest weight")
    class DummyPlayerTests {

        @Test
        @DisplayName("A zero-embedding fragment is dominated by a meaningful one")
        void zeroEmbedding_getsLowestWeight() {
            stubEmbedding("login form", unitVector(new double[]{1, 0, 0, 0}));
            stubEmbedding("zzz",        new double[]{0, 0, 0, 0});

            List<PromptFragment> result = service.computeShapleyAttribution(
                    "login form zzz",
                    List.of("login form", "zzz"),
                    STUB_UI
            );

            double loginWeight = findWeight(result, "login form");
            double dummyWeight = findWeight(result, "zzz");

            assertThat(loginWeight)
                    .as("Meaningful fragment should outweigh dummy")
                    .isGreaterThan(dummyWeight);
        }
    }

    // 5. Identical fragments – equal contributions → equal weights
    @Nested
    @DisplayName("Identical fragments receive equal weights")
    class IdenticalFragmentTests {

        @Test
        @DisplayName("Two fragments with identical embeddings get equal weights")
        void identicalEmbeddings_equalWeights() {
            double[] shared = unitVector(new double[]{1, 1, 0, 0});
            stubEmbedding("form a", shared);
            stubEmbedding("form b", shared);

            List<PromptFragment> result = service.computeShapleyAttribution(
                    "form a form b",
                    List.of("form a", "form b"),
                    STUB_UI
            );

            double wA = findWeight(result, "form a");
            double wB = findWeight(result, "form b");

            assertThat(wA).isCloseTo(wB, within(EPS));
        }
    }

    // 6. Edge cases
    @Nested
    @DisplayName("Edge cases: null / empty input handled gracefully")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null fragment list returns empty result without throwing")
        void nullFragments_returnsEmpty() {
            List<PromptFragment> result = service.computeShapleyAttribution(
                    "any prompt", null, STUB_UI);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Empty fragment list returns empty result without throwing")
        void emptyFragments_returnsEmpty() {
            List<PromptFragment> result = service.computeShapleyAttribution(
                    "any prompt", List.of(), STUB_UI);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Result size matches fragment count")
        void resultSize_matchesFragmentCount() {
            stubEmbedding("a", unitVector(new double[]{1, 0, 0, 0}));
            stubEmbedding("b", unitVector(new double[]{0, 1, 0, 0}));
            stubEmbedding("c", unitVector(new double[]{0, 0, 1, 0}));

            List<PromptFragment> result = service.computeShapleyAttribution(
                    "a b c", List.of("a", "b", "c"), STUB_UI);

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("Every result fragment carries a non-null mappedElement")
        void mappedElement_neverNull() {
            stubEmbedding("login", unitVector(new double[]{1, 0, 0, 0}));
            // Nadpisujemy ekstraktor dla 1 fragmentu, aby uniknąć IndexOutOfBoundsException
            when(htmlElementExtractor.extract(anyString())).thenReturn(List.of("form.login-form"));

            List<PromptFragment> result = service.computeShapleyAttribution(
                    "login", List.of("login"), STUB_UI);

            result.forEach(f ->
                    assertThat(f.mappedElement()).isNotNull()
            );
        }
    }

    // 7. Monotonicity – higher-relevance fragment should dominate
    @Nested
    @DisplayName("Monotonicity: fragment closer to UI target gets higher weight")
    class MonotonicityTests {

        @Test
        @DisplayName("Aligned fragment outweighs an orthogonal one")
        void alignedFragment_hasHigherWeight() {
            double[] targetEmb = unitVector(new double[]{1, 0, 0, 0});

            stubEmbedding("relevant", targetEmb);
            // Zmieniamy na {0, 0, 1, 0}, aby fragment nie pokrywał się z domyślnym wektorem przycisku {0, 1, 0, 0}
            stubEmbedding("noise",    unitVector(new double[]{0, 0, 1, 0}));

            List<PromptFragment> result = service.computeShapleyAttribution(
                    "relevant noise",
                    List.of("relevant", "noise"),
                    STUB_UI
            );

            double relevantW = findWeight(result, "relevant");
            double noiseW    = findWeight(result, "noise");

            assertThat(relevantW)
                    .as("Fragment aligned with target must have higher weight")
                    .isGreaterThan(noiseW);
        }
    }

    // Helpers
    /** Stub EmbeddingClient.embed(text) to return a fixed vector. */
    private void stubEmbedding(String text, double[] embedding) {
        when(embeddingClient.embed(text)).thenReturn(embedding);
    }

    /** Look up the weight for a fragment by its text. */
    private double findWeight(List<PromptFragment> fragments, String text) {
        return fragments.stream()
                .filter(f -> f.text().equals(text))
                .mapToDouble(PromptFragment::weight)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Fragment '" + text + "' not found in result"));
    }

    /**
     * L2-normalise a raw vector so cosine similarity is well-behaved.
     * This avoids accidentally creating zero-norm vectors during test setup.
     */
    private double[] unitVector(double[] v) {
        double norm = 0.0;
        for (double x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm < 1e-12) return v;
        double[] result = new double[v.length];
        for (int i = 0; i < v.length; i++) result[i] = v[i] / norm;
        return result;
    }
}