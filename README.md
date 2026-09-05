# Poké Ball Mouse 0.4.8

Eksperymentalna aplikacja Android wykorzystująca **Poké Ball Plus** jako jedno-ręczny kontroler systemu.

## Interfejs 0.4.8

- kompaktowy ekran główny — wyszukiwanie, łączenie i stan połączenia są pokazywane w tej samej karcie,
- Poké Ball w animacji zachowuje czerwono-białe kolory; zmienia się wyłącznie pierścień stanu,
- wyszukiwanie / łączenie: jasny biały animowany pierścień,
- połączenie: zielony pierścień i znacznik OK,
- statusy Bluetooth, Lokalizacji, Accessibility i Shizuku,
- neutralne jasne ikony, gdy dana funkcja jest dostępna; czerwone ikony i status, gdy jest wyłączona/brakuje dostępu,
- poziom baterii Poké Ball Plus odczytywany ze standardowej charakterystyki BLE Battery Level,
- usunięte niepotrzebne pola firmware oraz signal strength,
- jasny / ciemny / zgodny z systemem,
- polska i angielska wersja interfejsu,
- nowa ikona aplikacji: wycentrowany Poké Ball z kierunkami góra/dół/lewo/prawo, bez pasków/smyczy pod kulą.

## Tryby sterowania

### Mouse
- joystick: płynny kursor z dead-zone i akceleracją,
- klik joysticka: LPM,
- przytrzymanie kliknięcia + joystick: drag&drop przez Shizuku,
- krótki górny przycisk: PPM przez Shizuku; bez Shizuku fallback jako long-press Accessibility.

### D-pad / Navigation
- joystick: DPAD_UP / DOWN / LEFT / RIGHT,
- automatyczne powtarzanie kierunku po przytrzymaniu,
- klik joysticka: DPAD_CENTER / OK,
- krótki górny przycisk: BACK.

### Tap screen
Każdemu wejściu można przypisać dowolne miejsce ekranu:
- Joystick ↑ / ↓ / ← / →,
- klik joysticka,
- górny przycisk.

Punkty są zapisywane jako współrzędne znormalizowane. Z Shizuku przytrzymane kierunki są trwałymi punktami `SOURCE_TOUCHSCREEN`, dzięki czemu obsługiwany jest multitouch.

## Gesty ruchem

Przytrzymaj górny przycisk i wykonaj szybki ruch Poké Ballem. Dostępne są kierunki ← / → / ↑ / ↓, regulacja czułości oraz korekcja osi.

Domyślne akcje:
- Top + ←: Wstecz,
- Top + →: Ostatnie aplikacje,
- Top + ↑: Home,
- Top + ↓: Powiadomienia.

## Diagnostyka

Sekcja diagnostyczna pokazuje na żywo:
- stan górnego przycisku,
- stan kliknięcia joysticka,
- joystick X/Y,
- małą, wycentrowaną okrągłą wizualizację joysticka,
- Gyro X/Y/Z/W,
- Pitch / Yaw / Roll,
- Accel X/Y/Z,
- ostatni wykryty gest,
- stan BLE i Shizuku.

Dane orientacji są dekodowane zgodnie z publicznie reverse-engineerowanym formatem raportu Poké Ball Plus.

## Wymagania

- Android 10+ (`minSdk 29`),
- Bluetooth Low Energy,
- Poké Ball Plus,
- Accessibility Service,
- Shizuku — wymagane do pełnego D-pad, systemowego `SOURCE_MOUSE` i trwałego multitouch.

## Budowanie APK w Android Studio

1. Rozpakuj ZIP projektu.
2. Otwórz katalog `PokeballMouse` w Android Studio.
3. Poczekaj na synchronizację Gradle i pobranie zależności.
4. Wybierz `Build -> Build Bundle(s) / APK(s) -> Build APK(s)`.
5. APK debug znajdziesz w `app/build/outputs/apk/debug/app-debug.apk`.

Projekt używa `compileSdk 36`, Java 17, Android Gradle Plugin 8.10.0 i Shizuku API 13.1.5.

## Automatyczny build

`.github/workflows/build-apk.yml` buduje debug APK po pushu do `main` lub po ręcznym `workflow_dispatch`.

## Weryfikacja źródeł

Uruchom:

```bash
python3 tools/verify_source.py
```

Test sprawdza XML, dekoder joysticka/przycisków/akcelerometru/orientacji, gesty ruchowe i obecność wymaganych warstw Android/Shizuku.


### v0.4.8
- Adaptive launcher icon for rounded/squircle Android launcher masks.
- Light phone vibration when Poké Ball Plus is found during scanning.
- Phone vibration and sound tests in Diagnostics.
