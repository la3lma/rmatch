#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
framework_dir="${repo_root}/extended-benchmarking/regex_bench_framework"
image_tag="rmatch-bench-phase4-local:latest"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker not found in PATH" >&2
  exit 1
fi

mkdir -p "${framework_dir}/results"

echo "Building Docker image: ${image_tag}"
docker build \
  -f "${framework_dir}/Dockerfile" \
  -t "${image_tag}" \
  "${repo_root}"

echo "Running phase-4 smoke benchmark inside Docker"
docker run --rm -t \
  -v "${framework_dir}/results:/workspace/rmatch/extended-benchmarking/regex_bench_framework/results" \
  "${image_tag}" \
  /bin/bash -lc "cd /workspace/rmatch/extended-benchmarking/regex_bench_framework && ./scripts/run_phase4_smoke_in_container.sh"
