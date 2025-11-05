#!/usr/bin/env bash
# Helper script to collect system information and normalization benchmark
# for architecture-aware performance tracking.
# Outputs JSON to stdout.

set -euo pipefail

root_dir=$(git rev-parse --show-toplevel 2>/dev/null || echo ".")
cd "$root_dir"

MVN="./mvnw"
if [[ ! -x "$MVN" ]]; then
  MVN="mvn"
fi

# Ensure project is built
if [[ ! -d "rmatch-tester/target/classes" ]] || [[ ! -d "rmatch/target/classes" ]]; then
  echo "Building project for system info collection..." >&2
  $MVN -q -B -DskipTests package >&2 || exit 1
fi

# Create a temporary Java program to collect system info and run normalization benchmark
temp_java=$(mktemp)
cat > "$temp_java" <<'JAVA_CODE'
import no.rmz.rmatch.performancetests.utils.SystemInfo;
import no.rmz.rmatch.performancetests.utils.NormalizationBenchmark;
import java.util.Map;

public class CollectSystemInfo {
  public static void main(String[] args) {
    // Collect system information
    Map<String, Object> sysInfo = SystemInfo.collectSystemInfo();
    
    // Run normalization benchmark
    double normScore = NormalizationBenchmark.runBenchmarkMedian(3);
    
    // Generate architecture ID
    String archId = SystemInfo.generateArchitectureId(sysInfo);
    
    // Output as JSON
    System.out.println("{");
    
    // System info
    System.out.print("  \"system_info\": {");
    boolean first = true;
    for (Map.Entry<String, Object> entry : sysInfo.entrySet()) {
      if (!first) System.out.print(",");
      first = false;
      System.out.print("\n    \"" + entry.getKey() + "\": ");
      Object value = entry.getValue();
      if (value instanceof String) {
        System.out.print("\"" + escape((String)value) + "\"");
      } else if (value instanceof Number) {
        System.out.print(value);
      } else {
        System.out.print("\"" + value + "\"");
      }
    }
    System.out.println("\n  },");
    
    // Normalization data
    System.out.println("  \"normalization\": {");
    System.out.println("    \"score\": " + normScore + ",");
    System.out.println("    \"unit\": \"ops_per_ms\",");
    System.out.println("    \"description\": \"CPU normalization benchmark for cross-architecture comparison\"");
    System.out.println("  },");
    
    // Architecture ID
    System.out.println("  \"architecture_id\": \"" + archId + "\"");
    
    System.out.println("}");
  }
  
  private static String escape(String s) {
    return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
  }
}
JAVA_CODE

# Compile and run the temporary Java program
temp_class_dir=$(mktemp -d)
temp_class="$temp_class_dir/CollectSystemInfo.java"
cp "$temp_java" "$temp_class"

# Build classpath
CLASSPATH="rmatch-tester/target/classes:rmatch/target/classes"

# Try to get Guava from Maven classpath
GUAVA_JAR=$($MVN -q -B -f rmatch-tester/pom.xml dependency:build-classpath -Dmdep.outputFile=/dev/stdout 2>/dev/null | tr ':' '\n' | grep guava | head -1)
if [[ -n "$GUAVA_JAR" ]]; then
  CLASSPATH="$CLASSPATH:$GUAVA_JAR"
else
  # Fallback to standard Maven repository location
  for jar in ~/.m2/repository/com/google/guava/guava-*/guava-*.jar; do
    if [[ -f "$jar" ]]; then
      CLASSPATH="$CLASSPATH:$jar"
      break
    fi
  done
fi

# Compile and run
javac -cp "$CLASSPATH" -d "$temp_class_dir" "$temp_class" 2>&1 >&2
# Suppress Java logging by redirecting stderr, only output JSON to stdout
java -cp "$temp_class_dir:$CLASSPATH" -Djava.util.logging.level=OFF CollectSystemInfo 2>/dev/null

# Cleanup
rm -rf "$temp_class_dir" "$temp_java"
