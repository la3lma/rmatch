#!/usr/bin/env bash
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
echo "Building JMH module..." >&2

# In CI environments, ensure completely clean build to avoid annotation processing issues
if [[ -n "${CI:-}" ]] || [[ -n "${GITHUB_ACTIONS:-}" ]] || [[ -n "${JENKINS_URL:-}" ]]; then
  echo "CI environment detected, performing deep clean..." >&2
  $MVN -q -B -f benchmarks/jmh/pom.xml clean
  # Remove any potential stale generated sources
  rm -rf benchmarks/jmh/target/generated-sources || true
  rm -rf benchmarks/jmh/target/classes || true
fi

$MVN -U -q -B -f benchmarks/jmh/pom.xml -am -DskipTests clean package

# Verify annotation processing succeeded by checking for BenchmarkList
BENCHMARK_LIST="benchmarks/jmh/target/classes/META-INF/BenchmarkList"
if [[ ! -f "$BENCHMARK_LIST" ]]; then
  echo "WARNING: BenchmarkList not found at $BENCHMARK_LIST. Rebuilding with explicit annotation processing..." >&2
  $MVN -q -B -f benchmarks/jmh/pom.xml clean compile
  if [[ ! -f "$BENCHMARK_LIST" ]]; then
    echo "ERROR: Failed to generate BenchmarkList even after explicit compilation" >&2
    exit 1
  fi
fi
echo "BenchmarkList verified at: $BENCHMARK_LIST" >&2

# Locate the shaded jar (prefer the benchmarks jar)
JAR=$(ls -t benchmarks/jmh/target/rmatch-benchmarks-jmh-*-benchmarks.jar 2>/dev/null | head -n1 || true)
if [[ -z "$JAR" ]]; then
  JAR=$(ls -t benchmarks/jmh/target/rmatch-benchmarks-jmh-*.jar 2>/dev/null | head -n1 || true)
fi
if [[ -z "$JAR" ]]; then
  echo "ERROR: Could not find shaded JAR under benchmarks/jmh/target/" >&2
  exit 1
fi

# Verify the shaded JAR contains the BenchmarkList
echo "Verifying shaded JAR contains BenchmarkList..." >&2
if ! jar tf "$JAR" | grep -q "META-INF/BenchmarkList"; then
  echo "ERROR: Shaded JAR $JAR does not contain META-INF/BenchmarkList" >&2
  echo "JAR contents:" >&2
  jar tf "$JAR" | head -20 >&2
  exit 1
fi
echo "Shaded JAR verified: $JAR" >&2

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
  
  # First, make sure we have the latest build with annotation processing
  echo "Rebuilding with annotation processing..." >&2
  $MVN -q -B -f benchmarks/jmh/pom.xml -am -DskipTests clean compile
  
  # Remove any existing -f flag and add -f 0 for the fallback
  fallback_args=()
  skip_next=false
  for arg in "${args[@]}"; do
    if [[ $skip_next == true ]]; then
      skip_next=false
      continue
    fi
    if [[ $arg == "-f" ]]; then
      skip_next=true
      continue
    fi
    fallback_args+=("$arg")
  done
  fallback_args+=("-f" "0")
  
  # Try exec:java first
  set +e
  $MVN -q -B -f benchmarks/jmh/pom.xml -am -DskipTests \
    exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
    -Dexec.args="$(printf '%s ' "${fallback_args[@]}") \"$include\" ${*:-}"
  fallback_status=$?
  set -e
  
  if [[ $fallback_status -ne 0 ]]; then
    echo "exec:java also failed. Trying direct classpath execution..." >&2
    
    # Ensure we have the annotation processor output in target/classes
    if [[ ! -f "benchmarks/jmh/target/classes/META-INF/BenchmarkList" ]]; then
      echo "Re-running annotation processing for classpath execution..." >&2
      $MVN -q -B -f benchmarks/jmh/pom.xml clean compile
    fi
    
    # Build classpath and try to run directly
    echo "Building classpath..." >&2
    CLASSPATH=$($MVN -q -B -f benchmarks/jmh/pom.xml dependency:build-classpath -Dmdep.outputFile=/dev/stdout)
    CLASSPATH="$CLASSPATH:benchmarks/jmh/target/classes"
    
    # Verify BenchmarkList is accessible in classpath
    if [[ ! -f "benchmarks/jmh/target/classes/META-INF/BenchmarkList" ]]; then
      echo "ERROR: BenchmarkList still not found in target/classes after recompilation" >&2
      exit 1
    fi
    
    echo "Running JMH with direct classpath..." >&2
    java -cp "$CLASSPATH" org.openjdk.jmh.Main \
      "${fallback_args[@]}" "$include" ${*:-}
  fi
fi

# Emit friendly pointer
echo "JMH JSON: $JSON_OUT"
echo "JMH TXT : $TXT_OUT"
