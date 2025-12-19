#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/.build"

echo "Building rmatch benchmark engine..."

# Create build directory
mkdir -p "$BUILD_DIR"

# Check for Java
if ! command -v javac >/dev/null 2>&1; then
    echo "ERROR: javac not found. Please install a JDK."
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found. Please install a JRE/JDK."
    exit 1
fi

# Check for Maven
if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: mvn not found. Please install Maven."
    exit 1
fi

# Build and install rmatch to local .m2 repository if not already present
M2_DIR="$HOME/.m2/repository/no/rmz/rmatch/1.1-SNAPSHOT"
if [ ! -d "$M2_DIR" ]; then
    echo "rmatch not found in .m2 repository. Building and installing..."
    cd "$SCRIPT_DIR/../../../../.." && mvn clean install -DskipTests
    echo "✓ rmatch installed to local .m2 repository"
else
    echo "✓ rmatch found in .m2 repository"
fi

# Find the rmatch JAR file
RMATCH_JAR=$(find "$M2_DIR" -name "rmatch-1.1-SNAPSHOT.jar" | head -1)
if [ ! -f "$RMATCH_JAR" ]; then
    echo "ERROR: Could not find rmatch JAR in .m2 repository"
    exit 1
fi

echo "✓ Found rmatch JAR: $RMATCH_JAR"

# Copy rmatch dependencies
RMATCH_SOURCE_DIR="$SCRIPT_DIR/../../../../.."
RMATCH_TARGET_LIB="$RMATCH_SOURCE_DIR/rmatch/target/lib"
echo "Copying rmatch dependencies from $RMATCH_TARGET_LIB..."
mkdir -p "$BUILD_DIR/lib"
if [ -d "$RMATCH_TARGET_LIB" ]; then
    cp "$RMATCH_TARGET_LIB"/*.jar "$BUILD_DIR/lib/"
    echo "✓ Copied $(ls "$BUILD_DIR/lib" | wc -l) dependency JARs"
else
    echo "⚠️  rmatch dependencies not found at $RMATCH_TARGET_LIB - rebuilding rmatch..."
    cd "$RMATCH_SOURCE_DIR/rmatch" && mvn dependency:copy-dependencies -q
    if [ -d "$RMATCH_TARGET_LIB" ]; then
        cp "$RMATCH_TARGET_LIB"/*.jar "$BUILD_DIR/lib/"
        echo "✓ Copied $(ls "$BUILD_DIR/lib" | wc -l) dependency JARs"
    else
        echo "⚠️  Failed to get rmatch dependencies"
    fi
    cd "$SCRIPT_DIR"
fi

# Compile the benchmark
if [ -f "$SCRIPT_DIR/RMatchBenchmark.java" ]; then
    echo "Compiling rmatch benchmark..."
    javac -cp "$RMATCH_JAR:$BUILD_DIR/lib/*" -d "$BUILD_DIR" "$SCRIPT_DIR/RMatchBenchmark.java"
    echo "✓ rmatch benchmark compiled successfully"
else
    echo "Creating RMatchBenchmark.java..."
    cat > "$SCRIPT_DIR/RMatchBenchmark.java" << 'EOF'
import no.rmz.rmatch.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Benchmark harness for rmatch regex engine.
 *
 * Usage: java -cp rmatch.jar:. RMatchBenchmark patterns_file corpus_file
 *
 * Output format matches the standard benchmark format:
 * COMPILATION_NS=<nanoseconds>
 * ELAPSED_NS=<nanoseconds>
 * MATCHES=<count>
 * MEMORY_BYTES=<bytes>
 * PATTERNS_COMPILED=<count>
 * PATTERNS_FAILED=<count>
 */
public class RMatchBenchmark {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java RMatchBenchmark <patterns-file> <corpus-file>");
            System.exit(1);
        }

        String patternsFile = args[0];
        String corpusFile = args[1];

        try {
            // Read patterns
            List<String> patterns = Files.readAllLines(Paths.get(patternsFile));
            System.err.println("Loaded " + patterns.size() + " patterns");

            // Read corpus
            String corpus = Files.readString(Paths.get(corpusFile));
            System.err.println("Loaded corpus: " + corpus.length() + " chars");

            // Measure compilation time
            long compilationStart = System.nanoTime();

            RegexMatcher matcher = new RegexMatcher();
            int compiledCount = 0;
            int failedCount = 0;

            for (String pattern : patterns) {
                try {
                    matcher.add(pattern.trim());
                    compiledCount++;
                } catch (Exception e) {
                    System.err.println("Failed to compile pattern: " + pattern + " (" + e.getMessage() + ")");
                    failedCount++;
                }
            }

            long compilationEnd = System.nanoTime();
            long compilationNs = compilationEnd - compilationStart;

            // Measure memory before matching
            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            runtime.runFinalization();
            runtime.gc();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

            // Measure matching time
            long matchingStart = System.nanoTime();

            int totalMatches = 0;
            MatchCollector collector = new MatchCollector() {
                private int count = 0;

                @Override
                public void collectMatch(Match match) {
                    count++;
                }

                @Override
                public void endOfText() {
                    // No special handling needed
                }

                public int getCount() {
                    return count;
                }
            };

            if (compiledCount > 0) {
                matcher.findMatches(corpus, collector);
                if (collector instanceof CollectorWithCount) {
                    totalMatches = ((CollectorWithCount)collector).getCount();
                }
            }

            long matchingEnd = System.nanoTime();
            long matchingNs = matchingEnd - matchingStart;

            // Measure memory after matching
            runtime.gc();
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = Math.max(memoryAfter - memoryBefore, memoryAfter);

            // Output results in standard format
            System.out.println("COMPILATION_NS=" + compilationNs);
            System.out.println("ELAPSED_NS=" + matchingNs);
            System.out.println("MATCHES=" + totalMatches);
            System.out.println("MEMORY_BYTES=" + memoryUsed);
            System.out.println("PATTERNS_COMPILED=" + compiledCount);
            System.out.println("PATTERNS_FAILED=" + failedCount);

            System.err.println("Benchmark completed: " + compiledCount + " patterns, " + totalMatches + " matches");

        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Helper interface for counting matches
    private static interface CollectorWithCount {
        int getCount();
    }
}
EOF

    echo "Compiling rmatch benchmark..."
    javac -cp "$RMATCH_JAR" -d "$BUILD_DIR" "$SCRIPT_DIR/RMatchBenchmark.java"
    echo "✓ rmatch benchmark compiled successfully"
fi

# Create runner script with optimization flags
cat > "$BUILD_DIR/run_rmatch_benchmark.sh" << EOF
#!/bin/bash
# Generated rmatch benchmark runner with optimization flags
RMATCH_JAR="$RMATCH_JAR"
SCRIPT_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"

# Apply JVM optimization flags for maximum performance
JAVA_OPTS="-XX:+UseG1GC -Xmx2g -server"

# Enable rmatch AhoCorasick prefilter optimization (stable performance boost)
# Note: FastPath engine disabled due to hanging issues in current version
RMATCH_OPTS="-Drmatch.prefilter=aho"

exec java \$JAVA_OPTS \$RMATCH_OPTS -cp "\$SCRIPT_DIR:\$RMATCH_JAR:\$SCRIPT_DIR/lib/*" RMatchBenchmark "\$@"
EOF

chmod +x "$BUILD_DIR/run_rmatch_benchmark.sh"

echo "✓ Build complete!"
echo "  Executable: $BUILD_DIR/run_rmatch_benchmark.sh"
echo "  rmatch JAR: $RMATCH_JAR"
echo ""
echo "Test with:"
echo "  $BUILD_DIR/run_rmatch_benchmark.sh patterns.txt corpus.txt"