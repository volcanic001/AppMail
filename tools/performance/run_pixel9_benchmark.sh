#!/usr/bin/env bash
set -euo pipefail

# Strict Pixel 9/API 37 wrapper kept for the original Stage 0 contract.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

REQUIRED_MODEL_CONTAINS="${REQUIRED_MODEL_CONTAINS:-Pixel 9}" \
REQUIRED_DEVICE_CONTAINS="${REQUIRED_DEVICE_CONTAINS:-tokay}" \
REQUIRED_SDK="${REQUIRED_SDK:-37}" \
REFERENCE_LABEL="${REFERENCE_LABEL:-pixel9Api37}" \
"$SCRIPT_DIR/run_physical_benchmark.sh"
