#!/usr/bin/env bash
set -euo pipefail

# Controlled warmup test to investigate JIT effects on FastPath performance

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

MAX_REGEXPS=${1:-5000}
WARMUP_RUNS=${2:-5}
MEASUREMENT_RUNS=${3:-3}

echo "=== Warmup Benchmark Test ==="
echo "Patterns: $MAX_REGEXPS"
echo "Warmup runs: $WARMUP_RUNS"  
echo "Measurement runs: $MEASUREMENT_RUNS"
echo

# Test function that runs multiple iterations in the same JVM
run_warmup_test() {
    local engine_config="$1"
    local config_name="$2"
    
    echo "Testing: $config_name"
    echo "Config: $engine_config"
    
    # Create a temporary Java file that runs multiple iterations
    local temp_java=$(mktemp -t warmup_test).java
    local class_name=$(basename "$temp_java" .java)
    
    cat > "$temp_java" <<JAVA
import java.io.File;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarkerWithMemory;
import no.rmz.rmatch.performancetests.utils.WutheringHeightsBuffer;

public class $class_name {
    public static void main(String[] args) throws RegexpParserException {
        int maxRegexps = Integer.parseInt(args[0]);
        int warmupRuns = Integer.parseInt(args[1]);
        int measurementRuns = Integer.parseInt(args[2]);
        
        System.out.println("Starting warmup phase with " + warmupRuns + " runs...");
        
        // Warmup phase - results discarded
        for (int i = 0; i < warmupRuns; i++) {
            System.out.println("Warmup run " + (i + 1));
            long start = System.currentTimeMillis();
            runBenchmark(maxRegexps);
            long duration = System.currentTimeMillis() - start;
            System.out.println("Warmup run " + (i + 1) + ": " + duration + "ms");
        }
        
        System.out.println("\\nStarting measurement phase with " + measurementRuns + " runs...");
        
        // Measurement phase - results recorded
        long totalDuration = 0;
        for (int i = 0; i < measurementRuns; i++) {
            System.out.println("Measurement run " + (i + 1));
            long start = System.currentTimeMillis();
            runBenchmark(maxRegexps);
            long duration = System.currentTimeMillis() - start;
            System.out.println("Measurement run " + (i + 1) + ": " + duration + "ms");
            totalDuration += duration;
        }
        
        double averageDuration = (double) totalDuration / measurementRuns;
        System.out.println("\\nAverage measurement duration: " + averageDuration + "ms");
    }
    
    private static void runBenchmark(int maxRegexps) throws RegexpParserException {
        Matcher matcher = MatcherFactory.newMatcher();
        
        // Load patterns
        java.io.File regexpFile = new java.io.File("rmatch-tester/corpus/real-words-in-wuthering-heights.txt");
        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(regexpFile.toPath())) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < maxRegexps) {
                matcher.add(line, null);
                count++;
            }
        } catch (Exception e) {
            throw new RegexpParserException("Failed to load patterns", e);
        }
        
        // Run matching
        matcher.match(WutheringHeightsBuffer.getWutheringHeightsAsBuffer());
        
        // Cleanup
        try {
            matcher.shutdown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
JAVA
    
    # Compile and run the test
    local temp_dir=$(dirname "$temp_java")
    local classpath="rmatch/target/classes:rmatch-tester/target/classes"
    
    # Add all Maven dependencies to classpath
    for jar in $(find ~/.m2/repository -name "*.jar" 2>/dev/null | head -20); do
        classpath="$classpath:$jar"
    done
    
    echo "Compiling test..."
    javac -cp "$classpath" "$temp_java" -d "$temp_dir"
    
    echo "Running test with JIT warmup..."
    java -cp "$classpath:$temp_dir" $engine_config "$class_name" "$MAX_REGEXPS" "$WARMUP_RUNS" "$MEASUREMENT_RUNS"
    
    # Cleanup
    rm -f "$temp_java" "$temp_dir/$class_name.class"
    echo
}

# Ensure project is built
./mvnw -q -B -DskipTests package

echo "=== Testing Baseline (no optimizations) ==="
run_warmup_test "" "Baseline"

echo "=== Testing FastPath optimizations ==="
run_warmup_test "-Drmatch.engine=fastpath -Drmatch.prefilter=aho" "FastPath+AhoCorasick"

echo "=== Testing FastPath only (no prefilter) ==="  
run_warmup_test "-Drmatch.engine=fastpath" "FastPath-only"

echo "=== Warmup benchmark test completed ==="