#!/usr/bin/env bash
set -euo pipefail

# Run the macro benchmark (Wuthering Heights) via maven-exec-plugin.
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
LOG_OUT="benchmarks/results/macro-${stamp}.log"
JSON_OUT="benchmarks/results/macro-${stamp}.json"

# Build everything the tester depends on
$MVN -q -B -DskipTests package

sha=$(git rev-parse HEAD)
branch=$(git rev-parse --abbrev-ref HEAD)
java_ver=$(java -version 2>&1 | tr '\n' ' ')
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

set +e
$MVN -q -B -pl rmatch-tester -DskipTests \
  exec:java \
  -Dexec.mainClass=no.rmz.rmatch.performancetests.BenchmarkTheWutheringHeightsCorpus \
  -Dexec.args="${MAX_REGEXPS} ${EXTRA_ARGS:-}" \
  | tee "$LOG_OUT"
status=$?
set -e
end_ms=$(ms_now)
dur_ms=$(( end_ms - start_ms ))

cat > "$JSON_OUT" <<EOF
{
  "type": "macro",
  "timestamp": "${stamp}",
  "git": { "sha": "${sha}", "branch": "${branch}" },
  "java": "${java_ver}",
  "os": { "name": "${os_name}", "release": "${os_rel}" },
  "args": { "max_regexps": ${MAX_REGEXPS} },
  "duration_ms": ${dur_ms},
  "exit_status": ${status},
  "log": "${LOG_OUT}"
}
EOF

echo "Macro log : $LOG_OUT"
echo "Macro JSON: $JSON_OUT"
exit $status