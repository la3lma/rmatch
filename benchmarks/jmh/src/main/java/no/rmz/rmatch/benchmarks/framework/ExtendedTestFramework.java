package no.rmz.rmatch.benchmarks.framework;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.utils.CounterAction;
import no.rmz.rmatch.utils.StringBuffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Modern JMH-based testing framework for rmatch performance evaluation.
 *
 * <p>This framework replaces legacy CSV-based logging with structured JMH benchmarks, providing
 * comprehensive performance measurement with P95/P99 latencies and GitHub Actions compatibility.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>JMH-based benchmark execution with statistical confidence
 *   <li>Pattern library with 50+ categorized test patterns
 *   <li>Enhanced metrics including throughput, latency percentiles
 *   <li>GitHub Actions compatible reporting
 *   <li>Backward compatibility with existing baseline management
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(
    value = 1,
    jvmArgs = {"-Xms1G", "-Xmx1G"})
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
public class ExtendedTestFramework {

  private static final Logger LOG = Logger.getLogger(ExtendedTestFramework.class.getName());

  @Param({"10", "50", "100"})
  public int patternCount;

  @Param({"SIMPLE", "COMPLEX"})
  public String patternCategory;

  private PatternLibrary patternLibrary;
  private BenchmarkConfiguration config;
  private String testInput;

  @Setup(Level.Trial)
  public void setup() {
    LOG.info("Setting up ExtendedTestFramework with " + patternCount + " patterns");

    // Initialize pattern library and configuration
    patternLibrary = new DefaultPatternLibrary();
    config = BenchmarkConfiguration.defaultConfig();

    // Prepare for benchmark - no CSV logging dependencies
    patternLibrary.prepareForBenchmark(config);

    // Generate test input - using content similar to Wuthering Heights corpus
    generateTestInput();

    LOG.info("Setup complete: " + config);
  }

  private void generateTestInput() {
    final StringBuilder sb = new StringBuilder();

    // Generate realistic text content for pattern matching
    final String[] words = {
      "the",
      "and",
      "to",
      "of",
      "a",
      "in",
      "is",
      "it",
      "you",
      "that",
      "he",
      "was",
      "for",
      "on",
      "are",
      "as",
      "with",
      "his",
      "they",
      "i",
      "at",
      "be",
      "this",
      "have",
      "from",
      "or",
      "one",
      "had",
      "by",
      "word",
      "but",
      "not",
      "what",
      "all",
      "were",
      "we",
      "when",
      "your",
      "can",
      "said",
      "there",
      "each",
      "which",
      "she",
      "do",
      "how",
      "their",
      "if",
      "will",
      "up",
      "other",
      "about",
      "out",
      "many",
      "then",
      "them",
      "these",
      "so",
      "some",
      "her",
      "would",
      "make",
      "like",
      "into",
      "him",
      "has",
      "two",
      "more",
      "very",
      "after",
      "words",
      "first",
      "water",
      "been",
      "call",
      "who",
      "its",
      "now",
      "find",
      "long",
      "down",
      "day",
      "did",
      "get",
      "come",
      "made",
      "may",
      "part",
      "pattern",
      "match",
      "test",
      "benchmark",
      "performance",
      "regex",
      "framework",
      "email",
      "phone",
      "date",
      "url",
      "simple",
      "complex",
      "pathological"
    };

    // Create content that will match various pattern types
    for (int i = 0; i < 200; i++) {
      for (final String word : words) {
        sb.append(word).append(" ");
        if (i % 10 == 0) {
          // Add some structured content
          sb.append("test@example.com ");
          sb.append("2023-09-18 ");
          sb.append("(555) 123-4567 ");
          sb.append("https://example.com ");
        }
      }
      sb.append("\n");
    }

    testInput = sb.toString();
    LOG.info("Generated test input with " + testInput.length() + " characters");
  }

  /**
   * Main benchmark method for testing pattern compilation and matching.
   *
   * <p>This benchmark executes the complete pattern matching workflow: 1. Pattern compilation 2.
   * Text matching 3. Result collection
   *
   * <p>Results are compatible with GitHub Actions reporting and existing baseline management
   * systems.
   *
   * @param suite Test suite to execute (injected by JMH)
   * @return Test results with performance metrics
   */
  @Benchmark
  public TestResults runTestSuite(final Blackhole bh) {
    final long startTime = System.nanoTime();
    final Runtime runtime = Runtime.getRuntime();
    final long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

    try {
      // Get patterns for the specified category
      final PatternCategory category = PatternCategory.valueOf(patternCategory);
      final var patterns = patternLibrary.getPatternsByCategory(category);
      final var selectedPatterns = patterns.stream().limit(patternCount).toList();

      // Create matcher and add patterns
      final Matcher matcher = MatcherFactory.newMatcher();
      final CounterAction action = new CounterAction();

      for (final TestPattern pattern : selectedPatterns) {
        matcher.add(pattern.getRegex(), action);
      }

      // Perform matching
      final StringBuffer buffer = new StringBuffer(testInput);
      matcher.match(buffer);
      matcher.shutdown();

      // Calculate metrics
      final long endTime = System.nanoTime();
      final long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
      final long duration = endTime - startTime;
      final long memoryUsed = Math.max(0, memoryAfter - memoryBefore);

      final int matchCount = action.getCounter();
      final double throughput = (double) matchCount / TimeUnit.NANOSECONDS.toSeconds(duration);

      final TestResults results =
          new TestResults(
              "pattern_matching_" + category.name().toLowerCase(),
              duration,
              memoryUsed,
              matchCount,
              selectedPatterns.size(),
              throughput);

      // Note: Removed per-iteration logging to match sparse logging of previous system
      // Results are available in JMH JSON output for analysis

      bh.consume(results);
      return results;

    } catch (final Exception e) {
      LOG.severe("Benchmark execution failed: " + e.getMessage());
      throw new RuntimeException("Benchmark failed", e);
    }
  }

  /**
   * Benchmark focused on pattern compilation performance.
   *
   * @param bh JMH blackhole for result consumption
   * @return Compilation results
   */
  @Benchmark
  public TestResults patternCompilationBenchmark(final Blackhole bh) {
    final long startTime = System.nanoTime();
    final Runtime runtime = Runtime.getRuntime();
    final long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

    try {
      final PatternCategory category = PatternCategory.valueOf(patternCategory);
      final var patterns = patternLibrary.getPatternsByCategory(category);
      final var selectedPatterns = patterns.stream().limit(patternCount).toList();

      final Matcher matcher = MatcherFactory.newMatcher();
      final CounterAction action = new CounterAction();

      for (final TestPattern pattern : selectedPatterns) {
        matcher.add(pattern.getRegex(), action);
      }

      final long endTime = System.nanoTime();
      final long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
      final long duration = endTime - startTime;
      final long memoryUsed = Math.max(0, memoryAfter - memoryBefore);

      final double throughput =
          (double) selectedPatterns.size() / TimeUnit.NANOSECONDS.toSeconds(duration);

      final TestResults results =
          new TestResults(
              "pattern_compilation_" + category.name().toLowerCase(),
              duration,
              memoryUsed,
              0, // No matches in compilation benchmark
              selectedPatterns.size(),
              throughput);

      // Note: Removed per-iteration logging to match sparse logging of previous system
      // Results are available in JMH JSON output for analysis

      bh.consume(results);
      return results;

    } catch (final Exception e) {
      LOG.severe("Compilation benchmark failed: " + e.getMessage());
      throw new RuntimeException("Compilation benchmark failed", e);
    }
  }
}
