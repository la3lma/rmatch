package no.rmz.rmatch.performancetests;

import java.util.List;
import java.util.logging.Logger;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;

/**
 * Basic test runner for validating the performance comparison functionality. This class provides
 * both basic (small-scale) and comprehensive (large-scale) performance testing options.
 */
public final class BasicPerformanceTest {

  private static final Logger LOG = Logger.getLogger(BasicPerformanceTest.class.getName());

  public static void main(String[] args) {
    try {
      // Check if comprehensive test is requested
      boolean runComprehensive = args.length > 0 && "comprehensive".equals(args[0]);

      if (runComprehensive) {
        runComprehensiveTest(args);
      } else {
        runBasicTest();
      }

    } catch (Exception e) {
      LOG.severe("Test failed: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void runBasicTest() {
    LOG.info("Starting basic performance test validation");

    // Load baseline results (if any)
    List<MatcherBenchmarker.TestRunResult> baselineResults = BaselineManager.loadRmatchBaseline();
    LOG.info("Loaded " + baselineResults.size() + " baseline results");

    // Test with large-scale configuration to ensure regression signalling
    int maxRegexps = GitHubActionPerformanceTest.MIN_REGEXPS_FOR_REGRESSION;
    int numRuns = 3; // Minimum for statistical significance

    LOG.info(
        "Running regression-grade comparison with " + maxRegexps + " regexps and " + numRuns
            + " runs");

    GitHubActionPerformanceTest.ComparisonResult result =
        GitHubActionPerformanceTest.runComparison(maxRegexps, baselineResults);

    LOG.info("Basic test completed successfully!");
    LOG.info("Performance status: " + result.getPerformanceResult().getStatus());
    LOG.info("Explanation: " + result.getPerformanceResult().getExplanation());

    System.out.println("✅ Basic performance test validation completed successfully");
  }

  private static void runComprehensiveTest(String[] args) {
    LOG.info("Starting comprehensive performance test");

    // Load baseline results (if any)
    List<MatcherBenchmarker.TestRunResult> baselineResults = BaselineManager.loadRmatchBaseline();
    LOG.info("Loaded " + baselineResults.size() + " baseline results");

    System.out.println("=== LAUNCHING COMPREHENSIVE PERFORMANCE TEST ===");
    System.out.println(
        "This test uses the full Wuthering Heights corpus with thousands of regular expressions");
    System.out.println(
        "to provide realistic large-scale performance comparison between rmatch and Java regex.");
    System.out.println();

    // Parse additional args for comprehensive test (skip "comprehensive" argument)
    String[] comprehensiveArgs =
        args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[0];

    // Run comprehensive test
    ComprehensivePerformanceTest.ComprehensiveTestResult result;
    if (comprehensiveArgs.length > 0) {
      int maxRegexps = Integer.parseInt(comprehensiveArgs[0]);
      int numRuns = comprehensiveArgs.length > 1 ? Integer.parseInt(comprehensiveArgs[1]) : 1;

      ComprehensivePerformanceTest.ComprehensiveTestConfig config =
          new ComprehensivePerformanceTest.ComprehensiveTestConfig(
              "rmatch-tester/corpus/wuthr10.txt",
              "rmatch-tester/corpus/real-words-in-wuthering-heights.txt",
              maxRegexps,
              numRuns);

      result = ComprehensivePerformanceTest.runComprehensiveTest(config, baselineResults);
    } else {
      result = ComprehensivePerformanceTest.runComprehensiveTest(baselineResults);
    }

    // Print detailed results
    result.printSummary();

    System.out.println("✅ Comprehensive performance test completed successfully");

    // Provide guidance on results
    double ratio = result.getPerformanceRatio();
    if (ratio > 2.0) {
      System.out.println("🚀 Excellent! rmatch significantly outperforms Java regex");
    } else if (ratio > 1.0) {
      System.out.println("👍 Good! rmatch outperforms Java regex");
    } else if (ratio > 0.8) {
      System.out.println("⚖️  Performance is comparable between rmatch and Java regex");
    } else {
      System.out.println(
          "⚠️  rmatch underperforms compared to Java regex - investigate optimizations");
    }
  }
}
