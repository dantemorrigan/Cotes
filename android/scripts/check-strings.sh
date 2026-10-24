#!/usr/bin/env bash
# Все тексты интерфейса — в res/values*/strings.xml. Ищем кириллицу в строковых
# литералах Kotlin (комментарии не в счёт): такая строка не переведётся.
set -euo pipefail
export LC_ALL=C.UTF-8
cd "$(dirname "$0")/.."
hits=$(grep -rnP --include=*.kt '^(?!\s*(//|\*|/\*)).*"[^"]*[А-Яа-яЁё][^"]*"' app/src/main/java || true)
if [ -n "$hits" ]; then
  echo "Hardcoded Russian strings (move them to strings.xml):"
  echo "$hits"
  exit 1
fi
echo "no hardcoded strings"
