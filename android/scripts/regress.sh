#!/usr/bin/env bash
# Мини-регресс на эмуляторе: инструментальные тесты в светлой и тёмной теме,
# затем запуск релизного APK (проверка, что R8 ничего не сломал).
set -uo pipefail
PKG=io.github.dantemorrigan.cotes
OUT=build/regress
status=0
mkdir -p "$OUT"
adb shell input keyevent 82 || true
adb shell settings put system screen_off_timeout 1800000 || true
adb shell rm -rf /sdcard/cotes-shots

for mode in no yes; do
  name=$([ "$mode" = yes ] && echo dark || echo light)
  adb shell cmd uimode night "$mode"
  if ! ./gradlew --no-daemon connectedDebugAndroidTest; then status=1; fi
  mkdir -p "$OUT/report-$name"
  cp -r app/build/reports/androidTests/connected/. "$OUT/report-$name/" 2>/dev/null || true
  cp -r app/build/outputs/androidTest-results/connected/. "$OUT/report-$name/" 2>/dev/null || true
done

# Релизная сборка: ставим, запускаем, проверяем, что процесс жив.
adb shell cmd uimode night no
adb install -r app/build/outputs/apk/release/app-release.apk || status=1
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity" || status=1
sleep 4
if adb shell pidof "$PKG" >/dev/null; then echo "release app is running"; else echo "release app crashed"; status=1; fi
adb shell screencap -p /sdcard/cotes-shots/light-99-release-start.png
adb logcat -d -s AndroidRuntime:E > "$OUT/release-crash-log.txt" || true

adb pull /sdcard/cotes-shots "$OUT/shots" || true
ls -la "$OUT/shots" || true
exit $status
