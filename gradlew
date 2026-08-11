#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.13"
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BOOT_DIR="$ROOT_DIR/.gradle-bootstrap"
GRADLE_DIR="$BOOT_DIR/gradle-$GRADLE_VERSION"
ZIP="$BOOT_DIR/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
EXPECTED_SHA256="20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"

if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  mkdir -p "$BOOT_DIR"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..." >&2
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 "$URL" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "$URL"
    else
      echo "Error: install curl or wget, or install Gradle $GRADLE_VERSION manually." >&2
      exit 1
    fi
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA256=$(sha256sum "$ZIP" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA256=$(shasum -a 256 "$ZIP" | awk '{print $1}')
  else
    echo "Error: sha256sum or shasum is required to verify Gradle." >&2
    exit 1
  fi
  if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
    echo "Error: Gradle distribution checksum mismatch." >&2
    rm -f "$ZIP"
    exit 1
  fi
  command -v unzip >/dev/null 2>&1 || {
    echo "Error: unzip is required to bootstrap Gradle." >&2
    exit 1
  }
  rm -rf "$GRADLE_DIR"
  unzip -q "$ZIP" -d "$BOOT_DIR"
fi

exec "$GRADLE_DIR/bin/gradle" "$@"
