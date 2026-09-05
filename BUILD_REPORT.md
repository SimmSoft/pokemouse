# BUILD REPORT — Poké Ball Mouse 0.6.2

Pakiet źródłowy przygotowany na bazie `0.6.1`.

## Wprowadzone poprawki

1. **Sekcja połączenia**
   - przycisk `Połącz` nie pokazuje już ikony Bluetooth po lewej,
   - nadal pozostaje jedynym szerokim przyciskiem akcji zależnie od stanu połączenia.

2. **Profil / kalibracja urządzenia**
   - usunięto osobny przycisk `Profil`,
   - tekst z nazwą / ID / statusem kalibracji jest klikalny i otwiera dialog profilu,
   - dodano przycisk `Kalibracja Poké Ball Plus`, widoczny gdy profil nie jest jeszcze w pełni skalibrowany.

3. **Wizard kalibracji**
   - animowana kropka joysticka została powiększona,
   - zapis próbki gestu wykorzystuje teraz uśrednienie silniejszych fragmentów ruchu, z fallbackiem do najsilniejszego piku.

4. **Detekcja skalibrowanych gestów**
   - detektor nie łapie już wyłącznie pierwszego impulsu,
   - zbiera najlepsze dopasowanie do wzorca podczas przytrzymania `Top`,
   - przy bardzo wyraźnym ruchu może zadziałać od razu, a przy bardziej naturalnym flicku może potwierdzić kierunek po puszczeniu `Top`.

## Pliki zmienione

- `app/src/main/java/pl/openai/pokeballmouse/MainActivity.java`
- `app/src/main/java/pl/openai/pokeballmouse/CalibrationInstructionView.java`
- `app/src/main/java/pl/openai/pokeballmouse/CalibrationWizard.java`
- `app/src/main/java/pl/openai/pokeballmouse/CalibratedMotionGestureDetector.java`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-pl/strings.xml`
- `README.md`

## Uwaga

W tym środowisku nie wykonywałem pełnego buildu APK, więc pakiet jest dostarczony jako zaktualizowane źródła do dalszego spakowania / zbudowania lokalnie lub w CI.
