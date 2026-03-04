import com.google.re2j.Pattern;
import com.google.re2j.Matcher;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * RE2J Engine Benchmark
 *
 * Tests Google's RE2J (Java port of RE2) regex engine.
 * RE2J provides linear-time guarantees and avoids catastrophic backtracking.
 */
public class RE2JBenchmark {
    private static class BenchmarkResult {
        long compilationNs = 0;
        long scanningNs = 0;
        int matchCount = 0;
        long memoryUsedBytes = 0;
        int patternsCompiled = 0;
        int patternsFailed = 0;
    }

    private static final Runtime runtime = Runtime.getRuntime();

    private static List<String> loadPatterns(String filename) throws IOException {
        return Files.readAllLines(Paths.get(filename));
    }

    private static String loadCorpus(String filename) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filename)));
    }

    private static void forceGC() {
        // Try to force garbage collection for more accurate memory measurements
        runtime.gc();
        runtime.runFinalization();
        runtime.gc();

        // Give GC time to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java RE2JBenchmark <patterns-file> <corpus-file>");
            System.exit(1);
        }

        BenchmarkResult result = new BenchmarkResult();

        // Load inputs
        List<String> patternStrings = loadPatterns(args[0]);
        String corpus = loadCorpus(args[1]);

        System.err.printf("Loaded %d patterns, corpus size: %d bytes%n",
                         patternStrings.size(), corpus.length());

        // Force GC before measurement
        forceGC();

        // Measure compilation phase
        long beforeCompile = System.nanoTime();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        List<Pattern> compiledPatterns = new ArrayList<>();

        for (String patternStr : patternStrings) {
            try {
                // RE2J compilation - uses RE2 semantics, not Java semantics
                Pattern pattern = Pattern.compile(patternStr);
                compiledPatterns.add(pattern);
                result.patternsCompiled++;
            } catch (Exception e) {
                // RE2J may reject patterns that java.util.regex accepts
                // This is expected due to different feature support
                System.err.printf("RE2J rejected pattern: %s - %s%n",
                                 patternStr, e.getMessage());
                result.patternsFailed++;
            }
        }

        // Force GC to get accurate memory measurement
        forceGC();

        long afterCompile = System.nanoTime();
        long memAfterCompile = runtime.totalMemory() - runtime.freeMemory();

        result.compilationNs = afterCompile - beforeCompile;
        result.memoryUsedBytes = memAfterCompile - memBefore;

        System.err.printf("Successfully compiled %d/%d patterns%n",
                         result.patternsCompiled, patternStrings.size());

        if (compiledPatterns.isEmpty()) {
            System.err.println("ERROR: No patterns could be compiled!");
            System.exit(1);
        }

        // Measure scanning phase
        long beforeScan = System.nanoTime();

        // RE2J scanning - similar API to java.util.regex but different semantics
        for (Pattern pattern : compiledPatterns) {
            Matcher matcher = pattern.matcher(corpus);

            // Find all matches
            while (matcher.find()) {
                result.matchCount++;

                // Prevent JIT optimizations from eliminating the work
                if (matcher.start() < 0) {
                    throw new IllegalStateException("Invalid match position");
                }
            }
        }

        long afterScan = System.nanoTime();
        result.scanningNs = afterScan - beforeScan;

        // Output results in parseable format
        System.out.printf("COMPILATION_NS=%d%n", result.compilationNs);
        System.out.printf("ELAPSED_NS=%d%n", result.scanningNs);
        System.out.printf("MATCHES=%d%n", result.matchCount);
        System.out.printf("MEMORY_BYTES=%d%n", result.memoryUsedBytes);
        System.out.printf("PATTERNS_COMPILED=%d%n", result.patternsCompiled);
        System.out.printf("PATTERNS_FAILED=%d%n", result.patternsFailed);

        // Additional RE2J-specific information
        System.out.printf("ENGINE=RE2J%n");
        System.out.printf("LINEAR_TIME_GUARANTEE=true%n");
        System.out.printf("BACKTRACKING_SAFE=true%n");

        // Performance metrics
        double throughputMBps = (corpus.length() * compiledPatterns.size() / 1024.0 / 1024.0) /
                               (result.scanningNs / 1_000_000_000.0);
        System.out.printf("THROUGHPUT_MBPS=%.2f%n", throughputMBps);

        double patternsPerSecond = compiledPatterns.size() /
                                  (result.scanningNs / 1_000_000_000.0);
        System.out.printf("PATTERNS_PER_SECOND=%.0f%n", patternsPerSecond);
    }
}