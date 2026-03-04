#!/usr/bin/env bash
set -euo pipefail

cd /workspace/rmatch/benchmarking/framework/regex_bench_framework

python3 -m venv .venv
source .venv/bin/activate

pip install --upgrade pip setuptools wheel
pip install -e .

# Build only canonical smoke engines.
bash engines/java-native-naive/build.sh
bash engines/re2j/build.sh
bash engines/rmatch/build.sh

timestamp="$(date -u +%Y%m%d_%H%M%S)"
output_dir="results/phase4_smoke_${timestamp}"
mkdir -p "${output_dir}"

.venv/bin/regex-bench check-engines --output "${output_dir}"
.venv/bin/regex-bench run-phase \
  --config test_matrix/smoke_phase4_tiny.json \
  --output "${output_dir}" \
  --parallel 1

echo "SMOKE_OUTPUT_DIR=${output_dir}"
