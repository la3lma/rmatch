#!/usr/bin/env bash -x
set -euo pipefail

# Prefer running JMH from the shaded JAR; fall back to exec:java in-process (-f 0)
# Usage: scripts/run_jmh.sh [extra jmh args]
# Env vars:
#   JMH_INCLUDE   - regex for benchmark names to include (default: no\.rmz\.rmatch\.benchmarks\..*)
#   JMH_WARMUP    - e.g. '5s'
#   JMH_WARMUP_IT - warmup iterations, e.g. '5'
#   JMH_MEASURE   - e.g. '10s'
#   JMH_IT        - measurement iterations, e.g. '10'
#   JMH_FORKS     - e.g. '2'
#   JMH_THREADS   - e.g. '1'

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

MVN="./mvnw"; [[ -x "$MVN" ]] || MVN="mvn"

mkdir -p benchmarks/results
stamp=$(date -u +"%Y%m%dT%H%M%SZ")
JSON_OUT="benchmarks/results/jmh-${stamp}.json"
TXT_OUT="benchmarks/results/jmh-${stamp}.txt"

# Build shaded jar for the JMH module
$MVN -U -q -B -f benchmarks/jmh/pom.xml -am -DskipTests clean package

# Locate the shaded jar
JAR=$(ls -t benchmarks/jmh/target/rmatch-benchmarks-jmh-*.jar 2>/dev/null | head -n1 || true)
if [[ -z "$JAR" ]]; then
  echo "ERROR: Could not find shaded JAR under benchmarks/jmh/target/" >&2
  exit 1
fi

# Construct JMH CLI args
args=( -rf json -rff "$JSON_OUT" -o "$TXT_OUT" )
[[ -n "${JMH_WARMUP:-}"    ]] && args+=( -w  "$JMH_WARMUP" )
[[ -n "${JMH_WARMUP_IT:-}" ]] && args+=( -wi "$JMH_WARMUP_IT" )
[[ -n "${JMH_MEASURE:-}"   ]] && args+=( -r  "$JMH_MEASURE" )
[[ -n "${JMH_IT:-}"        ]] && args+=( -i  "$JMH_IT" )
[[ -n "${JMH_FORKS:-}"     ]] && args+=( -f  "$JMH_FORKS" )
[[ -n "${JMH_THREADS:-}"   ]] && args+=( -t  "$JMH_THREADS" )

include="${JMH_INCLUDE:-no\.rmz\.rmatch\.benchmarks\..*}"

# Try shaded jar first
set +e
java -jar "$JAR" "${args[@]}" "$include" ${*:-}
status=$?
set -e

if [[ $status -ne 0 ]]; then
  echo "Shaded jar run failed (status=$status). Falling back to exec:java (-f 0)." >&2
  $MVN -q -B -f benchmarks/jmh/pom.xml -am -DskipTests \
    exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
    -Dexec.args="$(printf '%s ' "${args[@]}") -f 0 \"$include\" ${*:-}"
fi

# Emit friendly pointer
echo "JMH JSON: $JSON_OUT"
echo "JMH TXT : $TXT_OUT"
