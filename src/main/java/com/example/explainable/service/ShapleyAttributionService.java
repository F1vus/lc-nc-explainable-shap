package com.example.explainable.service;

import com.example.explainable.client.EmbeddingClient;
import com.example.explainable.model.GeneratedUi;
import com.example.explainable.model.PromptFragment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShapleyAttributionService {

    private final EmbeddingClient embeddingClient;
    private final LlmFragmentMappingService fragmentMappingService;
    private final LlmHtmlElementExtractor htmlElementExtractor;

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Compute Shapley-valued attribution for each prompt fragment.
     *
     * @param prompt    the original user prompt (used for embedding baseline)
     * @param fragments fragments extracted from the prompt
     * @return list of {@link PromptFragment} with Shapley weights in [0, 1]
     */
    public List<PromptFragment> computeShapleyAttribution(String prompt, List<String> fragments, GeneratedUi ui) {
        if (fragments == null || fragments.isEmpty()) {
            log.warn("No fragments provided to ShapleyAttributionService");
            return List.of();
        }

        int n = fragments.size();
        log.info("Starting Shapley attribution: n={} fragments, 2^n={} coalitions", n, 1 << n);

        // ── Step 1: Embed the full prompt and every fragment ─────────────────
        // These are the only embedding API calls we make — O(n+1) total.
        // Everything else is pure linear algebra in memory.

        double[][] fragmentEmbeddings = new double[n][];
        for (int i = 0; i < n; i++) {
            fragmentEmbeddings[i] = embeddingClient.embed(fragments.get(i));
            log.debug("  [{}] embedded '{}' (dim={})", i, fragments.get(i), fragmentEmbeddings[i].length);
        }

        List<String> uiElements = htmlElementExtractor.extract(ui.html());
        if (!uiElements.contains("body")) uiElements.add("body");

        double[][] targetEmbeddings = new double[uiElements.size()][];
        for (int i = 0; i < uiElements.size(); i++) {
            targetEmbeddings[i] = embeddingClient.embed(uiElements.get(i));
        }

        // ── Step 2: Exact Shapley computation ────────────────────────────────
        double[] rawShapley = computeExactShapley(n, fragmentEmbeddings, targetEmbeddings);
        log.debug("Raw Shapley values: {}", Arrays.toString(rawShapley));

        // ── Step 3: Min-max normalise to [0, 1] for display ──────────────────
        double[] weights = softmax(rawShapley);

        // ── Step 4: Package results ──────────────────────────────────────────
        List<PromptFragment> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String mappedEl = fragmentMappingService.mapToUiElement(fragments.get(i), ui.html());
            result.add(new PromptFragment(fragments.get(i), weights[i], mappedEl));
            log.info("  Fragment '{}' → shapley={} normalised={} element={}",
                    fragments.get(i), rawShapley[i], weights[i], mappedEl);
        }
        System.out.println(result.get(1));
        return result;
    }

    // ─── Shapley core ────────────────────────────────────────────────────────

    /**
     * Iterates over all 2ⁿ coalitions (as bitmasks) and accumulates the
     * weighted marginal contributions for every player not yet in the coalition.
     *
     * <p>Algorithm sketch for coalition mask {@code m}:</p>
     * <pre>
     *   inCoalition  = { i : bit i of m is 1 }
     *   notInCoalition = { i : bit i of m is 0 }
     *   s = |inCoalition|
     *   weight = s! · (n−s−1)! / n!
     *
     *   for each player i in notInCoalition:
     *       φᵢ += weight · [ v(m | (1<<i)) − v(m) ]
     * </pre>
     */
    private double[] computeExactShapley(int n,
                                         double[][] fragmentEmbeddings,
                                         double[][] targetEmbeddings) {
        double[] shapley = new double[n];
        double[] factorial = precomputeFactorials(n);
        Map<Integer, Double> valueCache = new HashMap<>(1 << n);

        // Cache coalition values to avoid recomputing the same subset
        // when multiple players share the same base coalition.

        for (int mask = 0; mask < (1 << n); mask++) {
            // Identify players in / not in this coalition
            List<Integer> inCoalition    = new ArrayList<>(n);
            List<Integer> notInCoalition = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ((mask & (1 << i)) != 0 ? inCoalition : notInCoalition).add(i);
            }

            double vS = valueCache.computeIfAbsent(mask,
                    k -> coalitionValue(inCoalition, fragmentEmbeddings, targetEmbeddings));

            int s = inCoalition.size();
            if (s == n) continue;

            double weight = (factorial[s] * factorial[n - s - 1]) / factorial[n];

            for (int i : notInCoalition) {
                int maskWithI = mask | (1 << i);
                // Викликаємо coalitionValue з новим параметром
                double vSi = valueCache.computeIfAbsent(maskWithI,
                        k -> {
                            List<Integer> coalitionWithI = new ArrayList<>(inCoalition);
                            coalitionWithI.add(i);
                            return coalitionValue(coalitionWithI, fragmentEmbeddings, targetEmbeddings);
                        });

                shapley[i] += weight * (vSi - vS);
            }
        }
        return shapley;
    }

    // ─── Value function v(S) ─────────────────────────────────────────────────

    /**
     * v(S) — the characteristic function of the game.
     *
     * <p>Defined as the cosine similarity between:</p>
     * <ul>
     *   <li>the <em>mean embedding</em> of all fragments in S</li>
     *   <li>the embedding of the <em>full prompt</em></li>
     * </ul>
     *
     * <p>Intuition: a coalition "earns" value to the extent its fragments
     * collectively capture the semantic meaning of the original prompt.</p>
     *
     * <p>v(∅) = 0.0  (empty coalition gives no information)</p>
     */
    private double coalitionValue(List<Integer> coalition,
                                  double[][] fragmentEmbeddings,
                                  double[][] targetEmbeddings) {
        if (coalition.isEmpty()) return 0.0;

        // Рахуємо середній вектор коаліції (той самий calculateMeanEmbedding)
        double[] coalitionEmbedding = calculateMeanEmbedding(coalition, fragmentEmbeddings);

        // v(S) = наскільки добре ця група фрагментів "попадає" хоча б в один UI-елемент
        double maxSimilarity = 0.0;
        for (double[] targetEmb : targetEmbeddings) {
            double sim = cosineSimilarity(coalitionEmbedding, targetEmb);
            if (sim > maxSimilarity) {
                maxSimilarity = sim;
            }
        }

        return maxSimilarity;
    }

    // ─── Math helpers ────────────────────────────────────────────────────────

    private double[] calculateMeanEmbedding(List<Integer> coalition, double[][] fragmentEmbeddings) {
        int dim = fragmentEmbeddings[0].length;
        double[] mean = new double[dim];
        for (int idx : coalition) {
            for (int d = 0; d < dim; d++) {
                mean[d] += fragmentEmbeddings[idx][d];
            }
        }
        for (int d = 0; d < dim; d++) {
            mean[d] /= coalition.size();
        }
        return mean;
    }
    /**
     * Cosine similarity in ℝᵈ.  Returns 0 when either vector is near-zero.
     * sim(a, b) = (a · b) / (‖a‖ · ‖b‖)
     */
    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot  += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom < 1e-10 ? 0.0 : dot / denom;
    }

    /** Pre-compute 0! … n! to avoid repeated multiplications. */
    private double[] precomputeFactorials(int n) {
        double[] fact = new double[n + 1];
        fact[0] = 1.0;
        for (int i = 1; i <= n; i++) fact[i] = fact[i - 1] * i;
        return fact;
    }

    /**
     * Min-max normalisation.  Shapley values can be negative (a fragment can
     * hurt the coalition value), so we map the full range linearly to [0, 1].
     */
    private double[] softmax(double[] values) {
        double temp = values.length <= 3 ? 2 : 10;
        double sumExp = Arrays.stream(values).map(v -> Math.exp(v * temp)).sum();

        return Arrays.stream(values)
                .map(v -> Math.exp(v * temp) / sumExp)
                .toArray();
    }
}