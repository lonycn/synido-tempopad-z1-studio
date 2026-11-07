#!/bin/bash
# Reproducible local release packaging. Quit the player before running.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
./scripts/build.sh
python3 scripts/validate.py > tests/reports/package-validation.json
APP="$ROOT/dist/Z1 演奏助手.app"
"$APP/Contents/MacOS/Z1Player" --self-test | tee tests/reports/package-self-test.txt
VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Contents/Info.plist")"
BUILD="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$APP/Contents/Info.plist")"
ZIP="Z1-Studio-$VERSION-macOS-$(uname -m).zip"
STAGE="$(mktemp -d /tmp/z1-package.XXXXXX)"
trap 'rm -rf "$STAGE"' EXIT
/usr/bin/ditto -c -k --sequesterRsrc --keepParent "$APP" "$STAGE/$ZIP"
/usr/bin/ditto -x -k "$STAGE/$ZIP" "$STAGE/unpacked"
EXTRACTED="$STAGE/unpacked/Z1 演奏助手.app"
codesign --verify --deep --strict "$EXTRACTED"
"$EXTRACTED/Contents/MacOS/Z1Player" --self-test > tests/reports/package-unpacked-self-test.txt
cmp "$APP/Contents/MacOS/Z1Player" "$EXTRACTED/Contents/MacOS/Z1Player"
cmp "$APP/Contents/Resources/AppIcon.icns" "$EXTRACTED/Contents/Resources/AppIcon.icns"
mv "$STAGE/$ZIP" "$ROOT/dist/$ZIP"
chgrp "$(id -gn)" "$ROOT/dist/$ZIP"
(cd "$ROOT/dist" && shasum -a 256 "$ZIP" > "$ZIP.sha256" && shasum -a 256 -c "$ZIP.sha256")
printf '\n已打包 v%s (Build %s)：%s\n' "$VERSION" "$BUILD" "$ROOT/dist/$ZIP"
