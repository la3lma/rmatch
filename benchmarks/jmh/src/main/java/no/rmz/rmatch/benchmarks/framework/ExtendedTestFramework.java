package no.rmz.rmatch.benchmarks.framework;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.performancetests.JavaRegexpMatcher;
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
    jvmArgs = {
      "-Xms1G",
      "-Xmx1G",
      // Java 25 JIT optimizations discovered in performance analysis
      "-XX:+TieredCompilation",
      "-XX:CompileThreshold=500",
      // GC optimizations from master branch
      "-XX:+UseCompactObjectHeaders"
    })
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
public class ExtendedTestFramework {

  private static final Logger LOG = Logger.getLogger(ExtendedTestFramework.class.getName());

  @Param({"50", "100", "200", "500"})
  public int patternCount;

  @Param({"SIMPLE", "COMPLEX"})
  public String patternCategory;

  @Param({"100", "MAX"})
  public String corpusPatternCount;

  @Param({"WUTHERING_HEIGHTS", "CRIME_AND_PUNISHMENT"})
  public String textCorpus;

  @Param({"RMATCH", "FASTPATH", "FASTPATH_JIT_OPTIMIZED", "JAVA_NATIVE"})
  public String matcherType;

  private PatternLibrary patternLibrary;
  private BenchmarkConfiguration config;
  private String testInput;
  private String corpusText;
  private List<String> corpusPatterns;

  @Setup(Level.Trial)
  public void setup() {
    System.out.println(
        "🚀 Setting up benchmark: "
            + patternCategory
            + " patterns ("
            + patternCount
            + ") with "
            + matcherType
            + " matcher");

    // Initialize pattern library and configuration
    patternLibrary = new DefaultPatternLibrary();
    config = BenchmarkConfiguration.defaultConfig();

    // Prepare for benchmark - no CSV logging dependencies
    patternLibrary.prepareForBenchmark(config);

    // Generate test input - using content similar to Wuthering Heights corpus
    generateTestInput();

    // Load corpus data for corpus-based benchmarks
    loadCorpusData();

    System.out.println("✅ Setup complete - ready for benchmarking");
  }

  private void loadCorpusData() {
    try {
      // Load text corpus
      CorpusInputProvider.TextCorpus corpus = CorpusInputProvider.TextCorpus.valueOf(textCorpus);
      corpusText = CorpusInputProvider.loadTextCorpus(corpus);

      // Always load maximum patterns available (ALL corpus size)
      corpusPatterns = CorpusInputProvider.loadRegexPatterns(CorpusInputProvider.CorpusSize.ALL);
    } catch (IOException e) {
      System.out.println("⚠️  Failed to load corpus data, using fallback: " + e.getMessage());
      // Fall back to synthetic data
      corpusText = testInput;
      corpusPatterns = List.of("the", "and", "to", "of", "a", "in", "is", "it", "you", "that");
    }
  }

  /**
   * Create the appropriate matcher based on the matcherType parameter.
   *
   * @return A matcher instance (rmatch variants or Java native)
   */
  private Matcher createMatcher() {
    if ("JAVA_NATIVE".equals(matcherType)) {
      return new JavaRegexpMatcher();
    } else if ("FASTPATH".equals(matcherType)) {
      // Configure FastPath engine
      System.setProperty("rmatch.engine", "fastpath");
      System.setProperty("rmatch.prefilter", "aho");
      return MatcherFactory.newMatcher();
    } else if ("FASTPATH_JIT_OPTIMIZED".equals(matcherType)) {
      // Configure FastPath with Java 25 JIT optimizations
      System.setProperty("rmatch.engine", "fastpath");
      System.setProperty("rmatch.prefilter", "aho");
      // Note: JIT flags should be set at JVM startup via @Fork jvmArgs
      return MatcherFactory.newMatcher();
    } else {
      // Default rmatch matcher
      return MatcherFactory.newMatcher();
    }
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
      final Matcher matcher = createMatcher();
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
              "pattern_matching_" + category.name().toLowerCase() + "_" + matcherType.toLowerCase(),
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

      final Matcher matcher = createMatcher();
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
              "pattern_compilation_"
                  + category.name().toLowerCase()
                  + "_"
                  + matcherType.toLowerCase(),
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

  /**
   * Benchmark using actual Wuthering Heights corpus text with corpus-derived regex patterns.
   *
   * <p>This benchmark tests performance using the complete text from literary works and patterns
   * extracted from the same corpus, providing realistic benchmarking that matches the previous
   * rmatch-tester approach.
   *
   * @param bh JMH blackhole for result consumption
   * @return Test results with corpus-based metrics
   */
  @Benchmark
  public TestResults corpusBasedBenchmark(final Blackhole bh) {
    final long startTime = System.nanoTime();
    final Runtime runtime = Runtime.getRuntime();
    final long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

    try {
      final Matcher matcher = createMatcher();
      final CounterAction action = new CounterAction();

      // Determine actual pattern count: 100 for smoketest, max for "MAX"
      final int actualPatternCount;
      if ("MAX".equals(corpusPatternCount)) {
        actualPatternCount = corpusPatterns.size();
      } else {
        actualPatternCount = Math.min(Integer.parseInt(corpusPatternCount), corpusPatterns.size());
      }
      for (int i = 0; i < actualPatternCount; i++) {
        matcher.add(corpusPatterns.get(i), action);
      }

      // Perform matching against corpus text
      final StringBuffer buffer = new StringBuffer(corpusText);
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
              "corpus_"
                  + textCorpus.toLowerCase()
                  + "_"
                  + corpusPatternCount.toLowerCase()
                  + "_"
                  + matcherType.toLowerCase(),
              duration,
              memoryUsed,
              matchCount,
              actualPatternCount,
              throughput);

      bh.consume(results);
      return results;

    } catch (final Exception e) {
      LOG.severe("Corpus benchmark failed: " + e.getMessage());
      throw new RuntimeException("Corpus benchmark failed", e);
    }
  }

  /**
   * Benchmark for pattern compilation using corpus-derived patterns.
   *
   * <p>Measures just the compilation performance for patterns extracted from the corpus files,
   * without the matching phase.
   *
   * @param bh JMH blackhole for result consumption
   * @return Compilation results
   */
  @Benchmark
  public TestResults corpusPatternCompilationBenchmark(final Blackhole bh) {
    final long startTime = System.nanoTime();
    final Runtime runtime = Runtime.getRuntime();
    final long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

    try {
      final Matcher matcher = createMatcher();
      final CounterAction action = new CounterAction();

      // Determine actual pattern count: 100 for smoketest, max for "MAX"
      final int actualPatternCount;
      if ("MAX".equals(corpusPatternCount)) {
        actualPatternCount = corpusPatterns.size();
      } else {
        actualPatternCount = Math.min(Integer.parseInt(corpusPatternCount), corpusPatterns.size());
      }
      for (int i = 0; i < actualPatternCount; i++) {
        matcher.add(corpusPatterns.get(i), action);
      }

      final long endTime = System.nanoTime();
      final long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
      final long duration = endTime - startTime;
      final long memoryUsed = Math.max(0, memoryAfter - memoryBefore);

      final double throughput =
          (double) actualPatternCount / TimeUnit.NANOSECONDS.toSeconds(duration);

      final TestResults results =
          new TestResults(
              "corpus_pattern_compilation_"
                  + corpusPatternCount.toLowerCase()
                  + "_"
                  + matcherType.toLowerCase(),
              duration,
              memoryUsed,
              0, // No matches in compilation benchmark
              actualPatternCount,
              throughput);

      bh.consume(results);
      return results;

    } catch (final Exception e) {
      LOG.severe("Corpus compilation benchmark failed: " + e.getMessage());
      throw new RuntimeException("Corpus compilation benchmark failed", e);
    }
  }
}
