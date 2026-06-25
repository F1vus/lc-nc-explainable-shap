# Explainable SHAP-inspired UI Generator

Aplikacja Spring Boot MVC, która:

- przyjmuje prompt opisujący UI,
- dzieli go na fragmenty (przez LLM),
- generuje HTML/CSS interfejsu (Groq lub Gemini),
- liczy dokładne wartości Shapleya dla fragmentów promptu,
- pokazuje wykres atrybucji, test konsystencji (usunięcie top fragmentu) i wykres atrybucji po usunięciu,
- udostępnia tryb porównania Groq vs Gemini (`/compare`).

## Wymagania

- **Java 21**
- **Maven** (lub wrapper `./mvnw`, jeśli jest w repo)
- **Python 3.9+** — do lokalnego serwisu embeddingów
- Klucze API: **Groq** i **Gemini** (Gemini jest opcjonalny, jeśli korzystasz tylko z Groq, ale `application.properties` wymaga obu zmiennych środowiskowych, więc Gemini trzeba ustawić chociaż na puste/dowolne)

## 1. Konfiguracja kluczy API

Aplikacja czyta klucze z pliku `.env` (lub `.env.properties`) w katalogu głównym projektu — patrz `spring.config.import` w `application.properties`. Utwórz plik `.env` w katalogu głównym:

```properties
GROQ_API_KEY=twoj_klucz_groq
GEMINI_API_KEY=twoj_klucz_gemini
```

- Klucz Groq: https://console.groq.com/keys
- Klucz Gemini: https://aistudio.google.com/app/apikey

Model Groq użyty w kodzie: `llama-3.3-70b-versatile`.
Model Gemini użyty w kodzie: `gemini-2.5-flash`.

Alternatywnie możesz ustawić zmienne środowiskowe systemowo, bez pliku `.env`:

```bash
export GROQ_API_KEY=twoj_klucz_groq
export GEMINI_API_KEY=twoj_klucz_gemini
```

## 2. Serwis embeddingów (wymagany!)

Obliczanie wartości Shapleya wymaga embeddingów tekstu. `EmbeddingClient` w kodzie łączy się na sztywno z:

```
http://localhost:5000/embed
```

## 3. Budowa i odpalenie aplikacji

```bash
mvn clean install
mvn spring-boot:run
```

lub jednym poleceniem (jeśli masz `.jar` po `mvn package`):

```bash
java -jar target/explainable-shap-*.jar
```

Domyślny port: **8080**. Otwórz w przeglądarce:

```
http://localhost:8080
```

## 4. Co konfigurować — podsumowanie checklisty

| Element                  | Co zrobić                                                          |
|---------------------------|----------------------------------------------------------------------|
| `GROQ_API_KEY`            | klucz API z console.groq.com, w pliku `.env`                        |
| `GEMINI_API_KEY`          | klucz API z aistudio.google.com, w pliku `.env`                     |
| Serwis embeddingów        | własny Flask server na `localhost:5000/embed` (model `all-MiniLM-L6-v2`) — musi działać przed startem aplikacji |
| Java                      | wersja 21                                                            |
| Port aplikacji            | 8080 (domyślny, można zmienić w `application.properties` przez `server.port`) |

## Endpointy

- `GET /` — formularz generowania UI
- `POST /generate` — generuje UI, liczy atrybucję Shapleya, robi test konsystencji
- `GET /compare`, `POST /compare` — porównanie Groq vs Gemini na tym samym prompcie

## Ograniczenia

- Maks. **8 fragmentów** promptu — dokładne obliczenie Shapleya wymaga sprawdzenia 2ⁿ koalicji, powyżej 8 staje się zbyt kosztowne.
- Atrybucja jest inspirowana SHAP (oparta o podobieństwo kosinusowe embeddingów), nie jest to dokładny algorytm SHAP używany np. w modelach drzewiastych.
- Brak bazy danych — wszystko liczone w pamięci na żądanie, nic nie jest persystowane między requestami.