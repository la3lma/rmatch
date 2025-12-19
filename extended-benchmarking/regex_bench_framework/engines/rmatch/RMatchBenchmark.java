import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.utils.StringBuffer;
import java.io.*;
import java.nio.file.*;
import java.util.*;

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
    private static class CountingAction implements Action {
        private int count = 0;

        @Override
        public void performMatch(Buffer b, int start, int end) {
            count++;
        }

        public int getCount() {
            return count;
        }

        public void reset() {
            count = 0;
        }
    }

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
            System.err.flush();

            // Read corpus
            String corpus = Files.readString(Paths.get(corpusFile));
            System.err.println("Loaded corpus: " + corpus.length() + " chars");
            System.err.flush();

            // Measure compilation time
            long compilationStart = System.nanoTime();

            Matcher matcher = MatcherFactory.newMatcher();
            CountingAction countingAction = new CountingAction();

            int compiledCount = 0;
            // Note: No failedCount needed - we fail completely on any pattern compilation error

            for (String pattern : patterns) {
                try {
                    matcher.add(pattern.trim(), countingAction);
                    compiledCount++;
                } catch (Exception e) {
                    // FAIL COMPLETELY on any pattern compilation error - no silent failures
                    System.err.println("CRITICAL ERROR: Failed to compile pattern: " + pattern + " (" + e.getMessage() + ")");
                    System.err.println("Pattern compilation must be 100% successful for fair benchmarking.");
                    System.err.println("All patterns must be compatible with rmatch. Aborting.");
                    System.exit(2); // Exit code 2 indicates pattern compilation failure
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
            if (compiledCount > 0) {
                countingAction.reset();
                StringBuffer buffer = new StringBuffer(corpus);
                matcher.match(buffer);
                totalMatches = countingAction.getCount();
            }

            long matchingEnd = System.nanoTime();
            long matchingNs = matchingEnd - matchingStart;

            // Measure memory after matching
            runtime.gc();
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = Math.max(memoryAfter - memoryBefore, memoryAfter);

            // Output results in standard format with explicit flushing for framework compatibility
            System.out.println("COMPILATION_NS=" + compilationNs);
            System.out.flush(); // Ensure immediate output to framework
            System.out.println("ELAPSED_NS=" + matchingNs);
            System.out.flush();
            System.out.println("MATCHES=" + totalMatches);
            System.out.flush();
            System.out.println("MEMORY_BYTES=" + memoryUsed);
            System.out.flush();
            System.out.println("PATTERNS_COMPILED=" + compiledCount);
            System.out.flush();
            System.out.println("PATTERNS_FAILED=0"); // Always 0 - we fail completely on any compilation error
            System.out.flush();

            System.err.println("Benchmark completed: " + compiledCount + " patterns, " + totalMatches + " matches");
            System.err.flush(); // Ensure stderr is also flushed

        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}