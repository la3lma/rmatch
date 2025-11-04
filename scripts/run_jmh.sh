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
  
  # Verify JMH source file exists in CI
  JMH_SOURCE_DIR="benchmarks/jmh/src/main/java/no/rmz/rmatch/benchmarks"
  JMH_SOURCE_FILE="$JMH_SOURCE_DIR/CompileAndMatchBench.java"
  
  echo "CI: Checking for JMH source file at $JMH_SOURCE_FILE" >&2
  if [[ ! -f "$JMH_SOURCE_FILE" ]]; then
    echo "ERROR: JMH source file missing in CI environment: $JMH_SOURCE_FILE" >&2
    echo "CI: Available files in benchmarks/jmh/:" >&2
    find benchmarks/jmh/ -type f -name "*.java" 2>/dev/null | head -10 >&2 || true
    echo "CI: Directory structure:" >&2
    ls -la benchmarks/jmh/src/ 2>/dev/null >&2 || echo "CI: src directory doesn't exist" >&2
    exit 1
  fi
  echo "CI: JMH source file confirmed: $JMH_SOURCE_FILE" >&2
  
  # In CI, explicitly run annotation processing first, then package
  echo "CI: Step 1 - Running annotation processing..." >&2
  echo "CI: Java version and environment info:" >&2
  java -version >&2
  echo "CI: Maven version:" >&2
  $MVN --version >&2
  
  # First ensure all dependencies are available
  echo "CI: Resolving dependencies including parent project..." >&2
  $MVN -B -f benchmarks/jmh/pom.xml dependency:resolve -U >&2 || true
  $MVN -B -f benchmarks/jmh/pom.xml dependency:resolve-sources -U >&2 || true
  
  # Build parent project first to ensure rmatch dependency is available
  echo "CI: Building parent project dependencies..." >&2
  $MVN -B -f pom.xml -am -pl :rmatch -DskipTests clean install >&2 || true
  
  # Try annotation processing with verbose output in CI
  echo "CI: Attempting annotation processing with verbose output..." >&2
  $MVN -U -B -f benchmarks/jmh/pom.xml -am -DskipTests compile -X 2>&1 | tee /tmp/ci_compile_log.txt >&2 || true
  
  # Check compilation results
  echo "CI: Checking annotation processing results..." >&2
  if [[ -d "benchmarks/jmh/target/generated-sources/annotations" ]]; then
    echo "CI: Generated sources directory exists" >&2
    find benchmarks/jmh/target/generated-sources/annotations -name "*.java" | head -3 >&2 || true
  else
    echo "CI: Generated sources directory missing" >&2
  fi
  
  if [[ -d "benchmarks/jmh/target/classes" ]]; then
    echo "CI: Classes directory exists" >&2
    ls -la benchmarks/jmh/target/classes/ >&2 || true
    if [[ -d "benchmarks/jmh/target/classes/META-INF" ]]; then
      echo "CI: META-INF directory exists" >&2
      ls -la benchmarks/jmh/target/classes/META-INF/ >&2 || true
    fi
  else
    echo "CI: Classes directory missing" >&2
  fi
  
  # Verify annotation processing succeeded before proceeding
  if [[ ! -f "benchmarks/jmh/target/classes/META-INF/BenchmarkList" ]]; then
    echo "ERROR: CI annotation processing failed to generate BenchmarkList" >&2
    echo "CI: Checking compile log for errors:" >&2
    grep -i "error\|exception\|fail" /tmp/ci_compile_log.txt | tail -10 >&2 || true
    echo "CI: Last 20 lines of compile log:" >&2
    tail -20 /tmp/ci_compile_log.txt >&2 || true
    
    # Try alternative annotation processing approaches
    echo "CI: Attempting fallback annotation processing with explicit processor..." >&2
    $MVN -B -f benchmarks/jmh/pom.xml clean compile -Dmaven.compiler.proc=full 2>&1 >&2 || true
    
    # If still failing, try forcing annotation processor discovery
    if [[ ! -f "benchmarks/jmh/target/classes/META-INF/BenchmarkList" ]]; then
      echo "CI: Attempting explicit annotation processor setup..." >&2
      # Create a temporary Maven configuration to force annotation processing
      $MVN -B -f benchmarks/jmh/pom.xml dependency:resolve 2>&1 >&2 || true
      $MVN -B -f benchmarks/jmh/pom.xml clean compile -Dmaven.compiler.forceJavacCompilerUse=true -Dmaven.compiler.verbose=true 2>&1 >&2 || true
    fi
    
    if [[ ! -f "benchmarks/jmh/target/classes/META-INF/BenchmarkList" ]]; then
      echo "ERROR: All CI annotation processing attempts failed" >&2
      exit 1
    fi
  fi
  echo "CI: Step 1 complete - BenchmarkList generated" >&2
  
  echo "CI: Step 2 - Packaging with pre-generated annotations..." >&2
  $MVN -U -q -B -f benchmarks/jmh/pom.xml -am -DskipTests package
else
  # Local development - use single command
  $MVN -U -q -B -f benchmarks/jmh/pom.xml -am -DskipTests clean package
fi

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

# Augment JMH JSON with architecture information
echo "Adding architecture information to JMH results..." >&2
if [[ -f "$JSON_OUT" ]] && [[ -x "$root_dir/scripts/collect_system_info.sh" ]]; then
  system_info_json=$("$root_dir/scripts/collect_system_info.sh" 2>/dev/null || echo "{}")
  
  # Create augmented JSON with architecture info
  temp_json=$(mktemp)
  if command -v jq >/dev/null 2>&1; then
    # Use jq if available
    jq --argjson arch "$system_info_json" '. + {architecture: $arch}' "$JSON_OUT" > "$temp_json"
    mv "$temp_json" "$JSON_OUT"
  else
    # Fallback: manually add architecture field
    # Read the original JSON (it's an array)
    cat > "$temp_json" <<EOJSON
{
  "architecture": ${system_info_json},
  "benchmarks": $(cat "$JSON_OUT")
}
EOJSON
    mv "$temp_json" "$JSON_OUT"
  fi
  echo "Architecture information added to JMH results" >&2
fi
