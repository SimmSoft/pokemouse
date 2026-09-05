#!/usr/bin/env bash
set -euo pipefail
if ! command -v gradle >/dev/null 2>&1; then
  echo "Brak Gradle w PATH." >&2
  exit 1
fi
if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
  echo "Brak ANDROID_HOME/ANDROID_SDK_ROOT." >&2
  exit 1
fi
gradle --no-daemon :app:assembleDebug
printf '\nAPK: app/build/outputs/apk/debug/app-debug.apk\n'
