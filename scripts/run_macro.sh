#!/usr/bin/env bash
set -euo pipefail

# Run JMH in the benchmarks/jmh module and emit JSON & TXT results.
# Usage: scripts/run_jmh.sh [extra jmh args]
# Env vars:
#   JMH_INCLUDE   - regex for benchmark names to include
#   JMH_WARMUP    - e.g. '5s'
#   JMH_MEASURE   - e.g. '10s'
#   JMH_FORKS     - e.g. '2'
#   JMH_THREADS   - e.g. '1'
#   JAVA_HOME     - optional override

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

MVN="./mvnw"
if [[ ! -x "$MVN" ]]; then
  MVN="mvn"
fi

mkdir -p benchmarks/results
stamp=$(date -u +"%Y%m%dT%H%M%SZ")
JSON_OUT="benchmarks/results/jmh-${stamp}.json"
TXT_OUT="benchmarks/results/jmh-${stamp}.txt"

# Build the module first to ensure dependencies are compiled.
$MVN -q -B -pl benchmarks/jmh -am -DskipTests package

# Construct JMH args
JMH_ARGS=(
  "-rf" "json"
  "-rff" "$JSON_OUT"
  "-o" "$TXT_OUT"
)

if [[ -n "${JMH_INCLUDE:-}" ]]; then
  JMH_ARGS+=("-i" "$JMH_INCLUDE")
fi
if [[ -n "${JMH_WARMUP:-}" ]]; then
  JMH_ARGS+=("-w" "$JMH_WARMUP")
fi
if [[ -n "${JMH_MEASURE:-}" ]]; then
  JMH_ARGS+=("-r" "$JMH_MEASURE")
fi
if [[ -n "${JMH_FORKS:-}" ]]; then
  JMH_ARGS+=("-f" "$JMH_FORKS")
fi
if [[ -n "${JMH_THREADS:-}" ]]; then
  JMH_ARGS+=("-t" "$JMH_THREADS")
fi

# Run JMH via the plugin
$MVN -q -B -pl benchmarks/jmh -DskipTests \
  jmh:jmh -Djmh.failOnError=true \
  -Djmh.result.format=json -Djmh.result.file="$JSON_OUT" \
  -Djmh.output="$TXT_OUT" \
  ${JMH_ARGS[@]} -- ${@:-}

# Emit friendly pointer
echo "JMH JSON: $JSON_OUT"
echo "JMH TXT : $TXT_OUT"

