#!/bin/bash
# Build a native app for this Mac. No package downloads or network access.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/dist/Z1 演奏助手.app"
if /bin/ps -axo comm= | /usr/bin/grep -Fqx "$APP/Contents/MacOS/Z1Player"; then
  printf '请先退出 Z1 演奏助手，再重新构建。\n' >&2
  exit 1
fi
command -v swiftc >/dev/null || { printf '需要 Xcode Command Line Tools。\n' >&2; exit 1; }
mkdir -p "$ROOT/dist"
STAGE="$(mktemp -d "$ROOT/dist/.build.XXXXXX")"
cleanup() {
  # Restore the last working app if replacement was interrupted between renames.
  if [ ! -e "$APP" ] && [ -d "$STAGE/previous.app" ]; then
    mv "$STAGE/previous.app" "$APP"
  fi
  rm -rf "$STAGE"
}
trap cleanup EXIT
STAGED_APP="$STAGE/Z1 演奏助手.app"
mkdir -p "$STAGED_APP/Contents/MacOS" "$STAGED_APP/Contents/Resources"
cp "$ROOT/app/Info.plist" "$STAGED_APP/Contents/Info.plist"
cp "$ROOT/assets/audio/Z1_64pads_source.wav" "$STAGED_APP/Contents/Resources/"
cp "$ROOT/docs/PAD_MAP.txt" "$STAGED_APP/Contents/Resources/"
# Original vector-generated artwork. Regenerate with: swift scripts/generate_icon.swift
cp "$ROOT/app/Resources/AppIcon.icns" "$STAGED_APP/Contents/Resources/"
ARCH="$(uname -m)"
MIN_OS="$(/usr/libexec/PlistBuddy -c 'Print :LSMinimumSystemVersion' "$ROOT/app/Info.plist")"
swiftc -O -target "$ARCH-apple-macos$MIN_OS" "$ROOT"/src/*.swift \
  -o "$STAGED_APP/Contents/MacOS/Z1Player" \
  -framework AppKit -framework AVFoundation -framework CoreMIDI
codesign --force --deep --sign - "$STAGED_APP"
codesign --verify --deep --strict "$STAGED_APP"
# Replace the complete verified bundle, not an overlay of old and new files.
# Both renames stay on the same filesystem; cleanup restores on an interrupted swap.
if [ -e "$APP" ]; then mv "$APP" "$STAGE/previous.app"; fi
mv "$STAGED_APP" "$APP"
codesign --verify --deep --strict "$APP"
printf '\n构建完成：%s\n' "$APP"
printf '离线音频测试："%s/Contents/MacOS/Z1Player" --self-test\n' "$APP"
