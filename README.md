# Poké Ball Mouse 0.6.2

Eksperymentalna aplikacja Android wykorzystująca **Poké Ball Plus** jako jedno-ręczny kontroler telefonu.

## Najważniejsze funkcje

- Bluetooth LE: joystick, klik joysticka, przycisk Top, bateria i dane ruchu.
- Tryb **Mysz**: kursor, LPM, PPM/long-press i drag&drop.
- Tryb **D-pad / nawigacja**.
- Tryb **Tap ekranu** z przypisywaniem punktów i multitouch przez Shizuku.
- Gesty ruchowe `Top + ruch`.
- Profile per urządzenie z kalibracją joysticka i gestów.
- PL / EN oraz motyw System / Jasny / Ciemny.

## Zmiany 0.6.2

- Przycisk **Połącz** jest teraz pełnej szerokości i bez ikony Bluetooth po lewej.
- Usunięto osobny przycisk **Profil**; nazwa aktywnego Poké Balla jest klikalna i otwiera zarządzanie profilem.
- Po pominięciu kalibracji pojawia się osobny przycisk **Kalibracja Poké Ball Plus**.
- Animacja joysticka w kreatorze kalibracji ma większą kropkę, bliższą realnym proporcjom Poké Ball Plus.
- Kalibracja gestów zapisuje teraz bardziej reprezentatywną próbkę ruchu (uśrednienie mocniejszych fragmentów zamiast tylko jednego piku).
- Wykrywanie skalibrowanych gestów jest stabilniejsze dla krótkich ruchów nadgarstkiem: detektor zbiera najlepszą próbkę w trakcie przytrzymania Top i może finalizować kierunek przy puszczeniu przycisku.

## Wymagania

- Android 10+ (`minSdk 29`),
- Bluetooth Low Energy,
- Poké Ball Plus,
- Accessibility Service,
- Shizuku do pełnego D-pad, drag&drop i trwałego multitouch.

## Budowanie APK

Projekt używa `compileSdk 36`, Java 17, Android Gradle Plugin 8.10.0 i Shizuku API 13.1.5.

GitHub Actions: `.github/workflows/build-apk.yml` uruchamia najpierw:

```bash
python3 tools/verify_source.py
```

a następnie buduje:

```bash
gradle --no-daemon :app:assembleDebug
```

Gotowy APK: `app/build/outputs/apk/debug/app-debug.apk`.
