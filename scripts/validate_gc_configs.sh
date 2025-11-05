#!/usr/bin/env bash
set -euo pipefail

# Quick validation script to verify GC configurations work with Java 25
# This doesn't run full benchmarks - just validates that JVM accepts the flags

echo "Testing GC configuration flags with Java 25..."
echo ""

# Check Java version - portable approach without -P flag
java_version=$(java -version 2>&1 | head -n1 | grep -o '[0-9][0-9]*' | head -n1 || echo "unknown")
echo "Java version: $(java -version 2>&1 | head -n1)"
echo ""

if [[ "$java_version" != "25" ]]; then
  echo "⚠️  Warning: Java 25 not detected. Current version: $java_version"
  echo "   Set JAVA_HOME to Java 25 for full compatibility"
  echo ""
fi

# Function to test a GC configuration
test_gc_config() {
  local name="$1"
  local flags="$2"
  
  echo -n "Testing $name... "
  
  if [[ -z "$flags" ]]; then
    flags="-XX:+PrintFlagsFinal"
  else
    flags="$flags -XX:+PrintFlagsFinal"
  fi
  
  # Try to run Java with the flags (quote properly to handle spaces)
  if java $flags -version >/dev/null 2>&1; then
    echo "✓ OK"
    return 0
  else
    echo "✗ FAILED"
    echo "   Flags: $flags"
    java $flags -version 2>&1 | head -5 | sed 's/^/   /'
    return 1
  fi
}

echo "=== Testing GC Configurations ==="
echo ""

all_passed=0

# Test each configuration
test_gc_config "G1 (default)" "" || all_passed=$?
test_gc_config "ZGC Generational" "-XX:+UseZGC -XX:+ZGenerational" || all_passed=$?
test_gc_config "Shenandoah" "-XX:+UseShenandoahGC" || all_passed=$?
test_gc_config "G1 + Compact Headers" "-XX:+UseCompactObjectHeaders" || all_passed=$?
test_gc_config "G1 - Compact Headers" "-XX:-UseCompactObjectHeaders" || all_passed=$?
test_gc_config "ZGC + Compact Headers" "-XX:+UseZGC -XX:+ZGenerational -XX:+UseCompactObjectHeaders" || all_passed=$?
test_gc_config "Shenandoah + Compact Headers" "-XX:+UseShenandoahGC -XX:+UseCompactObjectHeaders" || all_passed=$?

echo ""
echo "=== Checking GC Availability ==="
echo ""

# Check which GCs are available
check_gc_available() {
  local gc_name="$1"
  local flag="$2"
  
  if java -XX:+PrintFlagsFinal -version 2>&1 | grep -q "$flag"; then
    local value=$(java -XX:+PrintFlagsFinal -version 2>&1 | grep "$flag" | awk '{print $4}')
    echo "  $gc_name: Available (current value: $value)"
  else
    echo "  $gc_name: Not found"
  fi
}

check_gc_available "G1 GC" "UseG1GC"
check_gc_available "ZGC" "UseZGC"
check_gc_available "Shenandoah GC" "UseShenandoahGC"
check_gc_available "Compact Object Headers" "UseCompactObjectHeaders"

echo ""
echo "=== Current GC Settings ==="
echo ""

# Show current GC being used
current_gc=$(java -XX:+PrintFlagsFinal -version 2>&1 | grep -E "Use.*GC\s+:?=" | grep "true" | awk '{print $1}' | head -1)
if [[ -n "$current_gc" ]]; then
  echo "  Active GC: $current_gc"
else
  echo "  Active GC: G1 (default)"
fi

compact_headers=$(java -XX:+PrintFlagsFinal -version 2>&1 | grep "UseCompactObjectHeaders" | awk '{print $4}')
echo "  Compact Object Headers: $compact_headers"

echo ""
if [[ $all_passed -eq 0 ]]; then
  echo "✓ All GC configurations validated successfully!"
  echo ""
  echo "You can now run benchmarks with different GC settings:"
  echo "  make bench-gc-experiments"
  echo "  scripts/run_gc_experiments.sh both all"
else
  echo "⚠️  Some GC configurations failed validation."
  echo ""
  echo "This may be due to:"
  echo "  - Java version mismatch (need Java 25)"
  echo "  - Platform limitations (some GCs may not be available on all platforms)"
  echo "  - JDK build configuration (some features may need to be enabled at build time)"
fi

exit $all_passed
