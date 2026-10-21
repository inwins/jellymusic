#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
    echo "Usage: $0 <version>  (example: $0 1.8.0)" >&2
    exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLE="${GRADLE:-/opt/gradle-8.7/bin/gradle}"
if [[ ! -x "$GRADLE" ]]; then
    GRADLE="gradle"
fi

echo "==> Building v$VERSION"
"$GRADLE" assembleDebug --console=plain

if [[ -n "$(git status --porcelain)" ]]; then
    echo "==> Committing changes"
    git add -A
    git commit -m "Release v$VERSION"
else
    echo "==> No changes to commit"
fi

if git rev-parse "v$VERSION" >/dev/null 2>&1; then
    echo "==> Tag v$VERSION already exists"
else
    echo "==> Tagging v$VERSION"
    git tag -a "v$VERSION" -m "Release v$VERSION"
fi

echo "==> Released v$VERSION"
