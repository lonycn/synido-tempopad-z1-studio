#!/bin/bash
# Update only the two symlinks created for this project. Never replace real files.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/dist/Z1 演奏助手.app"
[ -d "$APP" ] || { printf '请先运行 scripts/build.sh。\n' >&2; exit 1; }
link_one() {
  local target="$1" link="$2"
  if [ -e "$link" ] && [ ! -L "$link" ]; then
    printf '拒绝覆盖非符号链接：%s\n' "$link" >&2
    exit 1
  fi
  ln -sfn "$target" "$link"
  printf '%s → %s\n' "$link" "$target"
}
link_one "$APP" "$HOME/Desktop/Z1 演奏助手.app"
link_one "$ROOT" "$HOME/Desktop/Z1 工程与说明"
