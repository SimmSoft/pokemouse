# Poké Ball Mouse 0.4.0 — raport weryfikacji

## Zmiany względem 0.3.0

- status Lokalizacji na ekranie głównym,
- statusy ikon: jasna = aktywne/dostępne, czerwona = wyłączone/brak dostępu,
- czerwono-biały Poké Ball we wszystkich stanach połączenia,
- biały animowany pierścień dla SEARCHING/CONNECTING,
- zielony pierścień dla CONNECTED,
- kompaktowa animacja połączenia wewnątrz głównej karty zamiast osobnych ekranów,
- odczyt poziomu baterii BLE (`Battery Service 0x180F`, `Battery Level 0x2A19`),
- usunięte pola firmware i signal strength z UI,
- sekcja `Touch Mapping` przemianowana na `Tap screen` / `Tap ekranu`,
- pełny wybór języka PL/EN,
- dekodowanie Gyro X/Y/Z/W oraz Pitch/Yaw/Roll,
- okrągły, wycentrowany podgląd joysticka w Diagnostyce,
- nowa ikona aplikacji z Poké Ballem i czterema kierunkami,
- akcent interfejsu zmieniony z niebieskiego na czerwony Poké Ball; zwykłe ikony pozostają neutralne.

## Testy wykonane w sandboxie

- XML parse: PASS,
- DecoderSelfTest: PASS,
  - joystick min/center/max,
  - oba przyciski,
  - accelerometer X/Y/Z,
  - Gyro X/Y/Z/W,
  - Pitch/Yaw/Roll dla neutralnej orientacji,
- MotionGestureSelfTest: PASS,
- kontrola obecności:
  - BLE input UUID,
  - Battery Level UUID,
  - Accessibility overlay,
  - `SOURCE_MOUSE`,
  - `SOURCE_TOUCHSCREEN`,
  - D-pad,
  - Shizuku UserService,
  - PL/EN,
  - Location status,
  - ConnectionOrbView,
  - JoystickDiagnosticView,
  - launcher icon,
- kontrola, że pola firmware/signal strength nie występują w UI: PASS,
- parser `javac` pełnego drzewa: brak wykrytych błędów składni Java; pełna kompilacja wymaga Android SDK oraz zależności Android/Shizuku.

## Do sprawdzenia na fizycznym Poké Ball Plus

- czy dany egzemplarz udostępnia standardową charakterystykę Battery Level — jeżeli nie, UI pokaże `--%`,
- faktyczna orientacja osi ruchu zależna od chwytu — dlatego są swap/invert,
- zachowanie systemowego InputManager na konkretnym ROM-ie,
- opóźnienia BLE i multitouch w konkretnych grach.
