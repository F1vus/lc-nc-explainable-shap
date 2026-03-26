# Explainable SHAP-inspired UI Generator

Spring Boot MVC app that:

- accepts a prompt,
- extracts prompt fragments,
- generates HTML/CSS,
- shows a live preview in an iframe,
- calculates SHAP-inspired attribution,
- shows explainability and refinement suggestions.

## Run

```bash
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

## Notes

This project uses a heuristic attribution layer. It is SHAP-inspired, not exact SHAP.
