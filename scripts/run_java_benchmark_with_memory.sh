#!/usr/bin/env bash
set -euo pipefail

# Enhanced Java regex benchmark script that captures memory consumption data
# Run the Java regex benchmark (Wuthering Heights) with detailed memory tracking.
# Env vars:
#   MAX_REGEXPS   - default 10000
#   EXTRA_ARGS    - extra args to pass to the Java main, space-separated (optional)

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

MVN="./mvnw"
if [[ ! -x "$MVN" ]]; then
  MVN="mvn"
fi

MAX_REGEXPS=${MAX_REGEXPS:-10000}
mkdir -p benchmarks/results
stamp=$(date -u +"%Y%m%dT%H%M%SZ")
LOG_OUT="benchmarks/results/java-${stamp}.log"
JSON_OUT="benchmarks/results/java-${stamp}.json"

# Build everything the tester depends on
$MVN -q -B -DskipTests package

sha=$(git rev-parse HEAD)
branch=$(git rev-parse --abbrev-ref HEAD)
java_ver=$(java -version 2>&1 | tr '\n' ' ' | sed 's/"/\\"/g')
os_name=$(uname -s)
os_rel=$(uname -r)

# --- portable ms clock ---
ms_now() {
  if command -v python3 >/dev/null 2>&1; then
    python3 - <<'PY'
import time; print(int(time.time()*1000))
PY
  elif [[ -n "${EPOCHREALTIME:-}" ]]; then
    # bash 5+: EPOCHREALTIME like "1723241885.123456"
    awk -v t="$EPOCHREALTIME" 'BEGIN{split(t,a,"."); printf "%d", a[1]*1000 + substr(a[2]"000",1,3)}'
  else
    # fallback: seconds precision
    date +%s | awk '{print $1*1000}'
  fi
}
# --------------------------

start_ms=$(ms_now)

# Capture output to temporary file so we can parse memory data
temp_output=$(mktemp)

set +e
$MVN -q -B -pl rmatch-tester -DskipTests \
  exec:java \
  -Dexec.mainClass=no.rmz.rmatch.performancetests.TestJavaRegexpUnfairlyWithMemory \
  -Dexec.args="${MAX_REGEXPS} ${EXTRA_ARGS:-}" \
  > "$temp_output" 2>&1
status=$?
set -e

end_ms=$(ms_now)
dur_ms=$(( end_ms - start_ms ))

# Copy output to log file
cp "$temp_output" "$LOG_OUT"

# Parse memory statistics from output
parse_memory_stats() {
  local stats_file="$1"
  
  # Initialize default values
  memory_before_mb=0
  memory_after_mb=0  
  memory_used_mb=0
  memory_total_mb=0
  memory_max_mb=0
  
  # Parse basic memory stats
  if grep -q "MEMORY_STATS_BEGIN" "$stats_file"; then
    while IFS='=' read -r key value; do
      case "$key" in
        "memory_before_mb") memory_before_mb="$value" ;;
        "memory_after_mb") memory_after_mb="$value" ;;
        "memory_used_mb") memory_used_mb="$value" ;;
        "memory_total_mb") memory_total_mb="$value" ;;
        "memory_max_mb") memory_max_mb="$value" ;;
      esac
    done < <(sed -n '/MEMORY_STATS_BEGIN/,/MEMORY_STATS_END/p' "$stats_file" | grep '=')
  fi
  
  # Parse detailed memory stats if available
  memory_before_patterns_mb=0
  memory_after_patterns_mb=0
  memory_after_matching_mb=0
  memory_after_shutdown_mb=0
  memory_pattern_loading_mb=0
  memory_matching_mb=0
  memory_peak_used_mb=0
  
  if grep -q "DETAILED_MEMORY_STATS_BEGIN" "$stats_file"; then
    while IFS='=' read -r key value; do
      case "$key" in
        "memory_before_patterns_mb") memory_before_patterns_mb="$value" ;;
        "memory_after_patterns_mb") memory_after_patterns_mb="$value" ;;
        "memory_after_matching_mb") memory_after_matching_mb="$value" ;;
        "memory_after_shutdown_mb") memory_after_shutdown_mb="$value" ;;
        "memory_pattern_loading_mb") memory_pattern_loading_mb="$value" ;;
        "memory_matching_mb") memory_matching_mb="$value" ;;
        "memory_peak_used_mb") memory_peak_used_mb="$value" ;;
      esac
    done < <(sed -n '/DETAILED_MEMORY_STATS_BEGIN/,/DETAILED_MEMORY_STATS_END/p' "$stats_file" | grep '=')
  fi
}

# Parse memory data
parse_memory_stats "$temp_output"

# Generate JSON with memory data
cat > "$JSON_OUT" <<EOF
{
  "type": "java_regex",
  "timestamp": "${stamp}",
  "git": { "sha": "${sha}", "branch": "${branch}" },
  "java": "${java_ver}",
  "os": { "name": "${os_name}", "release": "${os_rel}" },
  "args": { "max_regexps": ${MAX_REGEXPS} },
  "duration_ms": ${dur_ms},
  "memory": {
    "before_mb": ${memory_before_mb},
    "after_mb": ${memory_after_mb},
    "used_mb": ${memory_used_mb},
    "total_mb": ${memory_total_mb},
    "max_mb": ${memory_max_mb},
    "detailed": {
      "before_patterns_mb": ${memory_before_patterns_mb},
      "after_patterns_mb": ${memory_after_patterns_mb},
      "after_matching_mb": ${memory_after_matching_mb},
      "after_shutdown_mb": ${memory_after_shutdown_mb},
      "pattern_loading_mb": ${memory_pattern_loading_mb},
      "matching_mb": ${memory_matching_mb},
      "peak_used_mb": ${memory_peak_used_mb}
    }
  },
  "exit_status": ${status},
  "log": "${LOG_OUT}"
}
EOF

# Clean up
rm -f "$temp_output"

echo "Java benchmark log : $LOG_OUT"
echo "Java benchmark JSON: $JSON_OUT"
exit $status