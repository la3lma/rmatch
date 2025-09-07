package no.rmz.rmatch.performancetests;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;

/**
 * Comprehensive performance test that uses large-scale corpus and many regular expressions to
 * provide realistic performance comparison between rmatch and Java regex implementations.
 *
 * <p>This test addresses the need for comprehensive testing beyond the basic small-scale tests. It
 * uses the full Wuthering Heights corpus with thousands of regular expressions to test performance
 * at realistic scale.
 */
public final class ComprehensivePerformanceTest {

  private static final Logger LOG = Logger.getLogger(ComprehensivePerformanceTest.class.getName());

  /** Default number of regular expressions for comprehensive testing (large scale) */
  private static final int DEFAULT_COMPREHENSIVE_REGEXPS = 5000;

  /** Maximum number of regular expressions for comprehensive testing */
  private static final int MAX_COMPREHENSIVE_REGEXPS = 10000;

  /** Number of runs for comprehensive testing (fewer needed due to large scale) */
  private static final int COMPREHENSIVE_RUNS = 3;

  /** Configuration for comprehensive performance testing */
  public static class ComprehensiveTestConfig {
    private final String corpusPath;
    private final String regexpPath;
    private final int maxRegexps;
    private final int numRuns;

    public ComprehensiveTestConfig(
        String corpusPath, String regexpPath, int maxRegexps, int numRuns) {
      this.corpusPath = checkNotNull(corpusPath, "Corpus path cannot be null");
      this.regexpPath = checkNotNull(regexpPath, "Regexp path cannot be null");
      this.maxRegexps = maxRegexps > 0 ? maxRegexps : DEFAULT_COMPREHENSIVE_REGEXPS;
      this.numRuns = numRuns > 0 ? numRuns : COMPREHENSIVE_RUNS;
    }

    public String getCorpusPath() {
      return corpusPath;
    }

    public String getRegexpPath() {
      return regexpPath;
    }

    public int getMaxRegexps() {
      return maxRegexps;
    }

    public int getNumRuns() {
      return numRuns;
    }
  }

  /** Result of comprehensive performance test with detailed metrics */
  public static class ComprehensiveTestResult {
    private final GitHubActionPerformanceTest.ComparisonResult comparisonResult;
    private final ComprehensiveTestConfig config;
    private final long totalTestTime;
    private final double rmatchThroughputMBps;
    private final double javaThroughputMBps;
    private final double performanceRatio;

    public ComprehensiveTestResult(
        GitHubActionPerformanceTest.ComparisonResult comparisonResult,
        ComprehensiveTestConfig config,
        long totalTestTime) {
      this.comparisonResult = comparisonResult;
      this.config = config;
      this.totalTestTime = totalTestTime;

      // Calculate throughput metrics
      double corpusSizeMB = getCorpusSizeMB();
      this.rmatchThroughputMBps =
          calculateThroughput(comparisonResult.getRmatchResults(), corpusSizeMB);
      this.javaThroughputMBps =
          calculateThroughput(comparisonResult.getJavaResults(), corpusSizeMB);
      this.performanceRatio =
          javaThroughputMBps > 0 ? rmatchThroughputMBps / javaThroughputMBps : 0;
    }

    private double calculateThroughput(
        List<MatcherBenchmarker.TestRunResult> results, double corpusSizeMB) {
      if (results.isEmpty()) return 0;
      double avgTimeSeconds =
          results.stream()
                  .mapToLong(MatcherBenchmarker.TestRunResult::durationInMillis)
                  .average()
                  .orElse(0)
              / 1000.0;
      return avgTimeSeconds > 0 ? corpusSizeMB / avgTimeSeconds : 0;
    }

    private double getCorpusSizeMB() {
      // Approximate size of Wuthering Heights corpus in MB
      return 0.675; // wuthr10.txt is about 675KB
    }

    public GitHubActionPerformanceTest.ComparisonResult getComparisonResult() {
      return comparisonResult;
    }

    public ComprehensiveTestConfig getConfig() {
      return config;
    }

    public long getTotalTestTime() {
      return totalTestTime;
    }

    public double getRmatchThroughputMBps() {
      return rmatchThroughputMBps;
    }

    public double getJavaThroughputMBps() {
      return javaThroughputMBps;
    }

    public double getPerformanceRatio() {
      return performanceRatio;
    }

    public void printSummary() {
      System.out.println("=== COMPREHENSIVE PERFORMANCE TEST RESULTS ===");
      System.out.printf("Test Configuration:%n");
      System.out.printf("  Corpus: %s%n", config.getCorpusPath());
      System.out.printf("  Regexps: %s%n", config.getRegexpPath());
      System.out.printf("  Max Regexps: %d%n", config.getMaxRegexps());
      System.out.printf("  Runs: %d%n", config.getNumRuns());
      System.out.printf("  Total Test Time: %d ms%n", totalTestTime);
      System.out.println();

      System.out.printf("Performance Metrics:%n");
      System.out.printf("  rmatch Throughput: %.3f MB/s%n", rmatchThroughputMBps);
      System.out.printf("  Java Throughput: %.3f MB/s%n", javaThroughputMBps);
      System.out.printf("  Performance Ratio (rmatch/java): %.2fx%n", performanceRatio);
      System.out.printf("  Status: %s%n", comparisonResult.getPerformanceResult().getStatus());

      if (performanceRatio > 1.0) {
        System.out.printf("  → rmatch is %.1fx FASTER than Java regex%n", performanceRatio);
      } else if (performanceRatio < 1.0 && performanceRatio > 0) {
        System.out.printf("  → rmatch is %.1fx SLOWER than Java regex%n", 1.0 / performanceRatio);
      }
      System.out.println();

      // Print memory usage comparison
      if (!comparisonResult.getRmatchResults().isEmpty()
          && !comparisonResult.getJavaResults().isEmpty()) {
        long avgRmatchMemory =
            comparisonResult.getRmatchResults().stream()
                    .mapToLong(MatcherBenchmarker.TestRunResult::usedMemoryInMb)
                    .sum()
                / comparisonResult.getRmatchResults().size();
        long avgJavaMemory =
            comparisonResult.getJavaResults().stream()
                    .mapToLong(MatcherBenchmarker.TestRunResult::usedMemoryInMb)
                    .sum()
                / comparisonResult.getJavaResults().size();

        System.out.printf("Memory Usage:%n");
        System.out.printf("  rmatch: %d MB%n", avgRmatchMemory);
        System.out.printf("  Java: %d MB%n", avgJavaMemory);
        if (avgJavaMemory > 0) {
          double memoryRatio = (double) avgRmatchMemory / avgJavaMemory;
          System.out.printf("  Memory Ratio (rmatch/java): %.2fx%n", memoryRatio);
        }
      }
    }
  }

  /** Private constructor for utility class */
  private ComprehensivePerformanceTest() {}

  /**
   * Create default configuration for comprehensive testing using full Wuthering Heights corpus and
   * large set of regular expressions.
   *
   * @return ComprehensiveTestConfig with default comprehensive test settings
   */
  public static ComprehensiveTestConfig createDefaultConfig() {
    return new ComprehensiveTestConfig(
        "rmatch-tester/corpus/wuthr10.txt", // Full Wuthering Heights corpus
        "rmatch-tester/corpus/real-words-in-wuthering-heights.txt", // Large regexp set
        DEFAULT_COMPREHENSIVE_REGEXPS,
        COMPREHENSIVE_RUNS);
  }

  /**
   * Create configuration for maximum scale comprehensive testing.
   *
   * @return ComprehensiveTestConfig with maximum scale settings
   */
  public static ComprehensiveTestConfig createMaxScaleConfig() {
    return new ComprehensiveTestConfig(
        "rmatch-tester/corpus/wuthr10.txt",
        "rmatch-tester/corpus/real-words-in-wuthering-heights.txt",
        MAX_COMPREHENSIVE_REGEXPS,
        COMPREHENSIVE_RUNS);
  }

  /**
   * Run comprehensive performance test with specified configuration and baseline comparison.
   *
   * @param config Test configuration
   * @param baselineResults Baseline results for comparison (can be empty)
   * @return ComprehensiveTestResult with detailed performance analysis
   */
  public static ComprehensiveTestResult runComprehensiveTest(
      ComprehensiveTestConfig config, List<MatcherBenchmarker.TestRunResult> baselineResults) {

    checkNotNull(config, "Config cannot be null");
    checkNotNull(baselineResults, "Baseline results cannot be null");

    LOG.info("Starting comprehensive performance test");
    LOG.info(
        String.format(
            "Config: corpus=%s, regexps=%s, maxRegexps=%d, runs=%d",
            config.getCorpusPath(),
            config.getRegexpPath(),
            config.getMaxRegexps(),
            config.getNumRuns()));

    long startTime = System.currentTimeMillis();

    try {
      // Run the comparison using existing infrastructure, but ensure minimum runs for comprehensive
      // test
      int actualRuns =
          Math.max(config.getNumRuns(), 3); // GitHubActionPerformanceTest requires minimum 3 runs

      GitHubActionPerformanceTest.ComparisonResult result =
          GitHubActionPerformanceTest.runComparison(
              config.getCorpusPath(),
              config.getRegexpPath(),
              config.getMaxRegexps(),
              actualRuns,
              baselineResults);

      long totalTime = System.currentTimeMillis() - startTime;

      ComprehensiveTestResult comprehensiveResult =
          new ComprehensiveTestResult(result, config, totalTime);

      LOG.info("Comprehensive performance test completed successfully");
      LOG.info(
          String.format(
              "Results: rmatch=%.3f MB/s, java=%.3f MB/s, ratio=%.2fx, status=%s",
              comprehensiveResult.getRmatchThroughputMBps(),
              comprehensiveResult.getJavaThroughputMBps(),
              comprehensiveResult.getPerformanceRatio(),
              result.getPerformanceResult().getStatus()));

      return comprehensiveResult;

    } catch (Exception e) {
      LOG.severe("Comprehensive performance test failed: " + e.getMessage());
      throw new RuntimeException("Comprehensive performance test failed", e);
    }
  }

  /**
   * Run comprehensive performance test with default configuration.
   *
   * @param baselineResults Baseline results for comparison
   * @return ComprehensiveTestResult with detailed performance analysis
   */
  public static ComprehensiveTestResult runComprehensiveTest(
      List<MatcherBenchmarker.TestRunResult> baselineResults) {
    return runComprehensiveTest(createDefaultConfig(), baselineResults);
  }

  /**
   * Main method for running comprehensive performance test from command line.
   *
   * @param args Command line arguments: [maxRegexps] [numRuns]
   */
  public static void main(String[] args) {
    try {
      LOG.info("Starting comprehensive performance test main");

      // Parse command line arguments
      int maxRegexps = DEFAULT_COMPREHENSIVE_REGEXPS;
      int numRuns = COMPREHENSIVE_RUNS;

      if (args.length > 0) {
        maxRegexps = Integer.parseInt(args[0]);
        if (maxRegexps <= 0) {
          maxRegexps = DEFAULT_COMPREHENSIVE_REGEXPS;
        }
      }

      if (args.length > 1) {
        numRuns = Integer.parseInt(args[1]);
        if (numRuns <= 0) {
          numRuns = COMPREHENSIVE_RUNS;
        }
      }

      LOG.info("Using maxRegexps=" + maxRegexps + ", numRuns=" + numRuns);

      // Create configuration
      ComprehensiveTestConfig config =
          new ComprehensiveTestConfig(
              "rmatch-tester/corpus/wuthr10.txt",
              "rmatch-tester/corpus/real-words-in-wuthering-heights.txt",
              maxRegexps,
              numRuns);

      // Load baseline results if available
      List<MatcherBenchmarker.TestRunResult> baselineResults = new ArrayList<>();
      try {
        baselineResults = BaselineManager.loadRmatchBaseline();
        LOG.info("Loaded " + baselineResults.size() + " baseline results");
      } catch (Exception e) {
        LOG.warning("Could not load baseline results: " + e.getMessage());
      }

      // Run comprehensive test
      ComprehensiveTestResult result = runComprehensiveTest(config, baselineResults);

      // Print results
      result.printSummary();

      // Exit successfully
      LOG.info("Comprehensive performance test completed successfully");

    } catch (Exception e) {
      LOG.severe("Comprehensive performance test failed: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}
