#!/usr/bin/env bash
# Расшифровывает ключ подписи для CI. Пароль — секрет RELEASE_KEYSTORE_PASSWORD.
# Без пароля ничего не делает: сборка подпишется отладочным ключом.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -z "${RELEASE_KEYSTORE_PASSWORD:-}" ]; then
  echo "RELEASE_KEYSTORE_PASSWORD is not set, release build will use the debug key"
  exit 0
fi
out="${RUNNER_TEMP:-/tmp}/cotes-release.p12"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -in release.keystore.enc -out "$out" -pass env:RELEASE_KEYSTORE_PASSWORD
echo "COTES_KEYSTORE_FILE=$out" >> "${GITHUB_ENV:-/dev/null}"
echo "release key ready"
