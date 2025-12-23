import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

/**
 * "Unfair" benchmark for Java's native regex engine.
 *
 * This implementation mimics the unfair test approach from rmatch repository:
 * - Sequential pattern matching (one pattern at a time)
 * - Looking for overlapping matches within the same text
 * - Uses naive approach that heavily favors rmatch's multi-pattern design
 *
 * This is intentionally unfair to Java regex to demonstrate rmatch's advantages.
 */
public class JavaNativeUnfairBenchmark {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java JavaNativeUnfairBenchmark <patterns-file> <corpus-file>");
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

            // Compile patterns sequentially (unfair approach)
            List<Pattern> compiledPatterns = new ArrayList<>();
            int compiledCount = 0;
            int failedCount = 0;

            for (String pattern : patterns) {
                try {
                    Pattern compiled = Pattern.compile(pattern.trim());
                    compiledPatterns.add(compiled);
                    compiledCount++;
                } catch (PatternSyntaxException e) {
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

            // Measure matching time - UNFAIR APPROACH
            long matchingStart = System.nanoTime();
            int totalMatches = 0;

            // Sequential matching - this is the "unfair" part
            // Each pattern is tested against the entire corpus separately
            // This is much less efficient than rmatch's unified NFA approach
            for (Pattern pattern : compiledPatterns) {
                Matcher matcher = pattern.matcher(corpus);

                // Find ALL matches (including overlapping ones)
                int pos = 0;
                while (pos < corpus.length()) {
                    matcher.reset(corpus);
                    matcher.region(pos, corpus.length());

                    if (matcher.find()) {
                        totalMatches++;
                        pos = matcher.start() + 1; // Move by 1 to find overlapping matches
                    } else {
                        break;
                    }
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

            System.err.println("Unfair benchmark completed: " + compiledCount + " patterns, " + totalMatches + " matches");

        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}