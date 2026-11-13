#!/usr/bin/env bash
# GBL-013 US-5 (wave 2) — CI contract for newpipe-gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/newpipe-plugin"

job="${1:-}"
case "$job" in
  fast|test)
    ./gradlew :newpipe:test --no-daemon --console=plain
    ;;
  *)
    printf 'unsupported job for newpipe-gradle: %s\n' "${job:-<none>}" >&2
    printf 'supported: fast | test\n' >&2
    exit 2
    ;;
esac
