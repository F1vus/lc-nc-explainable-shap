package com.example.explainable.service;

import com.example.explainable.client.EmbeddingClient;
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

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Compute Shapley-valued attribution for each prompt fragment.
     *
     * @param prompt    the original user prompt (used for embedding baseline)
     * @param fragments fragments extracted from the prompt
     * @return list of {@link PromptFragment} with Shapley weights in [0, 1]
     */
    public List<PromptFragment> computeShapleyAttribution(String prompt, List<String> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            log.warn("No fragments provided to ShapleyAttributionService");
            return List.of();
        }

        int n = fragments.size();
        log.info("Starting Shapley attribution: n={} fragments, 2^n={} coalitions", n, 1 << n);

        // ── Step 1: Embed the full prompt and every fragment ─────────────────
        // These are the only embedding API calls we make — O(n+1) total.
        // Everything else is pure linear algebra in memory.
        double[] promptEmbedding = embeddingClient.embed(prompt);
        double[][] fragmentEmbeddings = new double[n][];
        for (int i = 0; i < n; i++) {
            fragmentEmbeddings[i] = embeddingClient.embed(fragments.get(i));
            log.debug("  [{}] embedded '{}' (dim={})", i, fragments.get(i), fragmentEmbeddings[i].length);
        }

        // ── Step 2: Exact Shapley computation ────────────────────────────────
        double[] rawShapley = computeExactShapley(n, fragmentEmbeddings, promptEmbedding);
        log.debug("Raw Shapley values: {}", Arrays.toString(rawShapley));

        // ── Step 3: Min-max normalise to [0, 1] for display ──────────────────
        double[] weights = minMaxNormalize(rawShapley);

        // ── Step 4: Package results ──────────────────────────────────────────
        List<PromptFragment> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String mappedEl = mapElement(fragments.get(i));
            result.add(new PromptFragment(fragments.get(i), weights[i], mappedEl));
            log.info("  Fragment '{}' → shapley={:.4f} normalised={:.3f} element={}",
                    fragments.get(i), rawShapley[i], weights[i], mappedEl);
        }
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
                                         double[] promptEmbedding) {
        double[] shapley   = new double[n];
        double[] factorial = precomputeFactorials(n);

        // Cache coalition values to avoid recomputing the same subset
        // when multiple players share the same base coalition.
        Map<Integer, Double> valueCache = new HashMap<>(1 << n);

        for (int mask = 0; mask < (1 << n); mask++) {
            // Identify players in / not in this coalition
            List<Integer> inCoalition    = new ArrayList<>(n);
            List<Integer> notInCoalition = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ((mask & (1 << i)) != 0 ? inCoalition : notInCoalition).add(i);
            }

            double vS = valueCache.computeIfAbsent(mask,
                    k -> coalitionValue(inCoalition, fragmentEmbeddings, promptEmbedding));

            int s = inCoalition.size();
            // Shapley weight for coalitions of size s:  s!(n-s-1)!/n!
            double weight = (factorial[s] * factorial[n - s - 1]) / factorial[n];

            for (int i : notInCoalition) {
                int maskWithI = mask | (1 << i);
                List<Integer> coalitionWithI = new ArrayList<>(inCoalition);
                coalitionWithI.add(i);
                double vSi = valueCache.computeIfAbsent(maskWithI,
                        k -> coalitionValue(coalitionWithI, fragmentEmbeddings, promptEmbedding));

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
                                  double[] promptEmbedding) {
        if (coalition.isEmpty()) return 0.0;

        int dim = promptEmbedding.length;
        double[] coalitionEmbedding = new double[dim];

        // Mean-pool the embeddings of the coalition's fragments
        for (int idx : coalition) {
            double[] fe = fragmentEmbeddings[idx];
            for (int d = 0; d < dim; d++) {
                coalitionEmbedding[d] += fe[d];
            }
        }
        for (int d = 0; d < dim; d++) {
            coalitionEmbedding[d] /= coalition.size();
        }

        return cosineSimilarity(coalitionEmbedding, promptEmbedding);
    }

    // ─── Math helpers ────────────────────────────────────────────────────────

    /**
     * Cosine similarity in ℝᵈ.  Returns 0 when either vector is near-zero.
     *
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
    private double[] minMaxNormalize(double[] values) {
        double min = Arrays.stream(values).min().orElse(0.0);
        double max = Arrays.stream(values).max().orElse(1.0);
        double range = max - min;
        if (range < 1e-10) {
            return Arrays.stream(values).map(v -> 0.5).toArray();
        }
        return Arrays.stream(values).map(v -> (v - min) / range).toArray();
    }

    // ─── Semantic → UI element mapping ───────────────────────────────────────

    /**
     * Heuristically maps a fragment's text to a likely UI element type.
     * This is purely label-assignment for display — it does not affect weights.
     */
    private String mapElement(String fragment) {
        String f = fragment.toLowerCase(Locale.ROOT);
        if (f.contains("login")  || f.contains("sign"))       return "form";
        if (f.contains("dark")   || f.contains("theme"))      return "theme";
        if (f.contains("todo")   || f.contains("task"))       return "task list";
        if (f.contains("button") || f.contains("action"))     return "button";
        if (f.contains("card"))                                return "card";
        if (f.contains("input")  || f.contains("field"))      return "input";
        if (f.contains("nav")    || f.contains("menu"))       return "navigation";
        if (f.contains("table")  || f.contains("list"))       return "data table";
        if (f.contains("dashboard"))                           return "dashboard layout";
        if (f.contains("modal")  || f.contains("popup"))      return "modal";
        if (f.contains("search"))                              return "search bar";
        return "layout";
    }
}