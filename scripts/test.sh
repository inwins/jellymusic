#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
GRADLE=${GRADLE:-gradle}
"$GRADLE" testDebugUnitTest --console=plain "$@"
