#!/usr/bin/env bash
set -euo pipefail

bad="$(grep -RhoE 'uses:[[:space:]]+[^[:space:]#]+@[^[:space:]#]+' .github/workflows \
  | sed -E 's/.*@//' \
  | grep -Ev '^[0-9a-f]{40}$' || true)"
if [[ -n "$bad" ]]; then
  echo "::error::Every GitHub Action must be pinned to a full 40-character commit SHA."
  printf '%s\n' "$bad"
  exit 1
fi
