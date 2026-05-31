package com.example.explainable.controller;

import com.example.explainable.model.GeneratedUi;
import com.example.explainable.model.GenerationResult;
import com.example.explainable.model.PromptFragment;
import com.example.explainable.service.ExplanationService;
import com.example.explainable.service.LlmHtmlGenerationService;
import com.example.explainable.service.ShapleyAttributionService;
import com.example.explainable.service.impl.LlmPromptFragmentExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import com.example.explainable.model.GenerationRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeController – pipeline consistency tests")
class HomeControllerTest {

    @Mock LlmPromptFragmentExtractor extractor;
    @Mock LlmHtmlGenerationService htmlGenerationService;
    @Mock ShapleyAttributionService attributionService;
    @Mock ExplanationService explanationService;

    @InjectMocks
    HomeController controller;

    // ── Stable stubs ──────────────────────────────────────────────────────
    private static final GeneratedUi STUB_UI = new GeneratedUi(
            "<html><body><form class='login-form'></form></body></html>",
            "A simple login form",
            "Login UI"
    );

    private static final List<PromptFragment> STUB_FRAGMENTS = List.of(
            new PromptFragment("login form", 0.70, "form.login-form"),
            new PromptFragment("dark mode",  0.30, ".dark-mode-toggle")
    );

    // ── Helpers ───────────────────────────────────────────────────────────
    /** Build a GenerationRequest with the given prompt text. */
    private GenerationRequest req(String prompt) {
        GenerationRequest r = new GenerationRequest();
        r.setPrompt(prompt);
        return r;
    }

    /**
     * BindingResult with NO errors — simulates passed validation.
     */
    private BindingResult noErrors(GenerationRequest req) {
        return new BeanPropertyBindingResult(req, "generationRequest");
    }

    /**
     * BindingResult WITH a field error — simulates failed @NotBlank.
     */
    private BindingResult withErrors(GenerationRequest req) {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(req, "generationRequest");
        br.rejectValue("prompt", "NotBlank", "must not be blank");
        return br;
    }

    /** Stub all happy-path service calls. */
    private void stubHappyPath(boolean consistent) {
        when(extractor.extract(anyString()))
                .thenReturn(List.of("login form", "dark mode"));
        when(htmlGenerationService.generate(anyString()))
                .thenReturn(STUB_UI);
        when(attributionService.computeShapleyAttribution(
                anyString(), anyList(), any(GeneratedUi.class)))
                .thenReturn(STUB_FRAGMENTS);
        when(explanationService.explain(anyString(), anyList()))
                .thenReturn("The login form is the primary component.");
        when(explanationService.consistencyTest(anyString(), anyString()))
                .thenReturn(consistent);
        when(explanationService.refinePromptSuggestions(anyString()))
                .thenReturn(List.of("Add a page type", "List components"));
    }

    /** Extract the GenerationResult placed in the model by the controller. */
    private GenerationResult resultFrom(Model model) {
        return (GenerationResult) model.asMap().get("result");
    }

    // 1. GET /
    @Nested
    @DisplayName("GET / – index page")
    class GetIndexTests {

        @Test
        @DisplayName("Returns 'index' view name")
        void getIndex_returnsIndexView() {
            Model model = new ExtendedModelMap();
            String view = controller.index(model);
            assertThat(view).isEqualTo("index");
        }

        @Test
        @DisplayName("Model contains 'generationRequest' attribute")
        void getIndex_hasGenerationRequestInModel() {
            Model model = new ExtendedModelMap();
            controller.index(model);
            assertThat(model.containsAttribute("generationRequest")).isTrue();
        }
    }

    // 2. POST /generate – validation errors stay on index
    @Nested
    @DisplayName("POST /generate – validation failures stay on index")
    class ValidationTests {

        @Test
        @DisplayName("Binding errors → returns 'index' view")
        void withBindingErrors_returnsIndex() {
            GenerationRequest req = req("");
            Model model = new ExtendedModelMap();

            String view = controller.generate(req, withErrors(req), model);

            assertThat(view).isEqualTo("index");
        }

        @Test
        @DisplayName("Binding errors → no 'result' placed in model")
        void withBindingErrors_noResultInModel() {
            GenerationRequest req = req("  ");
            Model model = new ExtendedModelMap();

            controller.generate(req, withErrors(req), model);

            assertThat(model.containsAttribute("result")).isFalse();
        }
    }

    // 3. POST /generate – happy path
    @Nested
    @DisplayName("POST /generate – result model consistency")
    class ResultModelTests {

        @Test
        @DisplayName("Valid prompt → returns 'result' view")
        void validPrompt_rendersResultView() {
            stubHappyPath(true);
            GenerationRequest req = req("Create a login page with dark mode");
            Model model = new ExtendedModelMap();

            String view = controller.generate(req, noErrors(req), model);

            assertThat(view).isEqualTo("result");
        }

        @Test
        @DisplayName("Model always contains 'result' attribute")
        void resultModel_hasResultAttribute() {
            stubHappyPath(true);
            Model model = new ExtendedModelMap();
            controller.generate(req("login page"), noErrors(req("login page")), model);

            assertThat(model.containsAttribute("result")).isTrue();
        }

        @Test
        @DisplayName("Result html is never null")
        void resultModel_htmlNotNull() {
            stubHappyPath(true);
            Model model = new ExtendedModelMap();
            controller.generate(req("login page"), noErrors(req("login page")), model);

            assertThat(resultFrom(model).getHtml()).isNotNull();
        }

        @Test
        @DisplayName("Result fragments list is never null")
        void resultModel_fragmentsNotNull() {
            stubHappyPath(true);
            Model model = new ExtendedModelMap();
            controller.generate(req("login page"), noErrors(req("login page")), model);

            assertThat(resultFrom(model).getFragments()).isNotNull();
        }

        @Test
        @DisplayName("Result explanation is never null")
        void resultModel_explanationNotNull() {
            stubHappyPath(true);
            Model model = new ExtendedModelMap();
            controller.generate(req("login page"), noErrors(req("login page")), model);

            assertThat(resultFrom(model).getExplanation()).isNotNull();
        }

        @Test
        @DisplayName("shapleyUsed is always true in current implementation")
        void resultModel_shapleyUsedIsTrue() {
            stubHappyPath(true);
            Model model = new ExtendedModelMap();
            controller.generate(req("login page"), noErrors(req("login page")), model);

            assertThat(resultFrom(model).isShapleyUsed()).isTrue();
        }

        @Test
        @DisplayName("consistent flag is true when service returns true")
        void consistentFlag_trueWhenServiceReturnsTrue() {
            stubHappyPath(true);
            Model model = new ExtendedModelMap();
            controller.generate(req("Create a login page with dark mode"),
                    noErrors(req("Create a login page with dark mode")), model);

            assertThat(resultFrom(model).isConsistent()).isTrue();
        }

        @Test
        @DisplayName("consistent flag is false when service returns false")
        void consistentFlag_falseWhenServiceReturnsFalse() {
            stubHappyPath(false);
            Model model = new ExtendedModelMap();
            controller.generate(req("x"), noErrors(req("x")), model);

            assertThat(resultFrom(model).isConsistent()).isFalse();
        }

        @Test
        @DisplayName("Suggestions list is never null or empty")
        void resultModel_suggestionsPresent() {
            stubHappyPath(true);
            Model model = new ExtendedModelMap();
            controller.generate(req("dashboard with cards"),
                    noErrors(req("dashboard with cards")), model);

            assertThat(resultFrom(model).getSuggestions()).isNotEmpty();
        }
    }

    // 4. Prompt is preserved verbatim in the result model
    @Test
    @DisplayName("Original prompt is preserved verbatim in the result model")
    void promptPreservedInResult() {
        String inputPrompt = "Create a todo list app with priorities";
        stubHappyPath(true);
        Model model = new ExtendedModelMap();

        controller.generate(req(inputPrompt), noErrors(req(inputPrompt)), model);

        assertThat(resultFrom(model).getPrompt()).isEqualTo(inputPrompt);
    }
}
