

---

## 🔹 Pełne wyjaśnienie

Twój projekt to system typu **explainable AI**, który analizuje wpływ poszczególnych fragmentów promptu na wygenerowany interfejs użytkownika (HTML/CSS).

Użytkownik wpisuje opis, system generuje UI, a następnie określa, **które części tego opisu miały największy wpływ na końcowy wynik**.

---

## 🔹 Podstawa teoretyczna

Projekt opiera się na:

* **teorii gier**
* oraz metodzie **SHAP (Shapley values)**

W teorii gier mamy:

* graczy,
* koalicje,
* wspólny wynik,
* i problem: **jak sprawiedliwie podzielić wkład między graczy**.

W Twoim przypadku:

* **gracze** → fragmenty promptu
* **koalicje** → różne kombinacje fragmentów
* **wynik (value)** → wygenerowany interfejs
* **Shapley value** → wkład danego fragmentu w ten wynik

---

## 🔹 Jak działa SHAP u Ciebie

Dla każdego fragmentu system sprawdza:

* co się stanie, gdy fragment jest obecny,
* co się stanie, gdy go nie ma,
* oraz jak wpływa w różnych kombinacjach z innymi fragmentami.

Na tej podstawie obliczana jest wartość Shapleya, czyli:

> średni wpływ danego fragmentu na wynik, liczony w sposób sprawiedliwy i niezależny od kolejności.

---

## 🔹 Pipeline projektu

1. Prompt dzielony jest na fragmenty
   np.:

    * „dashboard for todo app”
    * „dark theme”
    * „login”

2. Model LLM generuje HTML/CSS

3. SHAP oblicza wagę każdego fragmentu

4. System mapuje fragmenty na elementy UI
   np.:

    * „login” → `form.login-form`
    * „todo app” → `ul.todo-list`
    * „dark theme” → `body.dark-theme`

5. LLM generuje krótkie wyjaśnienie w języku naturalnym

---

## 🔹 Co jest „wynikiem gry”

To важливий момент.

Nie chodzi o „ładniejszy design”.

Chodzi o:

> rozkład wpływu fragmentów promptu na wygenerowany interfejs.

Czyli „wynik” to:

* semantyczna zgodność UI z promptem
* i to, jak bardzo każdy fragment przyczynił się do tej zgodności

---

## 🔹 Kryteria oceny

Możesz powiedzieć, że oceniasz:

* **faithfulness (wierność)** — czy wysoka waga faktycznie oznacza realny wpływ
* **stability (stabilność)** — czy wyniki są podobne między uruchomieniami
* **relevance (trafność)** — czy fragment odpowiada właściwemu elementowi UI
* **consistency** — czy usunięcie ważnego fragmentu zmienia wynik

---

## 🔹 Jednozdaniowe podsumowanie

> W projekcie traktujemy prompt jako zbiór graczy, a metodą SHAP obliczamy sprawiedliwy wkład każdego fragmentu w wygenerowany interfejs HTML/CSS. Następnie mapujemy te fragmenty na konkretne elementy UI i generujemy zrozumiałe wyjaśnienie dla użytkownika.

---

## 🔹 Krótka wersja (na obronę, 20–30 sek)

> To system explainable AI dla generowania interfejsów. Dzielimy prompt na fragmenty, generujemy HTML/CSS, a następnie używamy SHAP i teorii gier, żeby określić, które części promptu miały największy wpływ na wynik. Na końcu mapujemy te fragmenty na konkretne elementy interfejsu i generujemy krótkie wyjaśnienie dla użytkownika.

---

Якщо хочеш — можу ще підготувати тобі:

* **питання, які точно задасть викладач**
* і **готові відповіді на них (типу “а чому саме SHAP?”)**
