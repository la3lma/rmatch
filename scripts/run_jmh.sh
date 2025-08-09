#!/usr/bin/env bash -x
set -euo pipefail

# Run JMH (pluginless) using exec-maven-plugin and emit JSON & TXT results.
# Usage: scripts/run_jmh.sh [extra jmh args]
# Env vars:
#   JMH_INCLUDE   - regex for benchmark names to include (default: no\.rmz\.rmatch\.benchmarks\..*)
#   JMH_WARMUP    - e.g. '5s'
#   JMH_WARMUP_IT - warmup iterations, e.g. '5'
#   JMH_MEASURE   - e.g. '10s'
#   JMH_IT        - measurement iterations, e.g. '10'
#   JMH_FORKS     - e.g. '2'
#   JMH_THREADS   - e.g. '1'
#   JAVA_HOME     - optional override

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

MVN="./mvnw"; [[ -x "$MVN" ]] || MVN="mvn"

mkdir -p benchmarks/results
stamp=$(date -u +"%Y%m%dT%H%M%SZ")
JSON_OUT="benchmarks/results/jmh-${stamp}.json"
TXT_OUT="benchmarks/results/jmh-${stamp}.txt"

# Build the reactor so the generated JMH harness is compiled
$MVN -U -q -B -pl benchmarks/jmh -am -DskipTests clean package

# Construct JMH CLI args
args=( -rf json -rff "$JSON_OUT" -o "$TXT_OUT" )
[[ -n "${JMH_WARMUP:-}"    ]] && args+=( -w  "$JMH_WARMUP" )
[[ -n "${JMH_WARMUP_IT:-}" ]] && args+=( -wi "$JMH_WARMUP_IT" )
[[ -n "${JMH_MEASURE:-}"   ]] && args+=( -r  "$JMH_MEASURE" )
[[ -n "${JMH_IT:-}"        ]] && args+=( -i  "$JMH_IT" )
[[ -n "${JMH_FORKS:-}"     ]] && args+=( -f  "$JMH_FORKS" )
[[ -n "${JMH_THREADS:-}"   ]] && args+=( -t  "$JMH_THREADS" )

include="${JMH_INCLUDE:-no\.rmz\.rmatch\.benchmarks\..*}"

# Run JMH main (no plugin)
# $MVN -q -B -pl benchmarks/jmh -am -DskipTests       exec:java -Dexec.mainClass=org.openjdk.jmh.Main       -Dexec.args="$(printf '%s ' "${args[@]}") ${include} ${*:-}"
$MVN -q -B -f benchmarks/jmh/pom.xml -am -DskipTests \
  exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="$(printf '%s ' "${args[@]}") ${include} ${*:-}"

echo "JMH JSON: $JSON_OUT"
echo "JMH TXT : $TXT_OUT"
