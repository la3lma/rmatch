package no.rmz.rmatch.performancetests;

import java.util.List;
import java.util.logging.Logger;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;

/** Basic test runner for validating the performance comparison functionality. */
public final class BasicPerformanceTest {

  private static final Logger LOG = Logger.getLogger(BasicPerformanceTest.class.getName());

  public static void main(String[] args) {
    try {
      LOG.info("Starting basic performance test validation");

      // Load baseline results (if any)
      List<MatcherBenchmarker.TestRunResult> baselineResults = BaselineManager.loadRmatchBaseline();
      LOG.info("Loaded " + baselineResults.size() + " baseline results");

      // Test with minimal configuration
      int maxRegexps = 100; // Very small test
      int numRuns = 3; // Minimum for statistical significance

      LOG.info("Running comparison with " + maxRegexps + " regexps and " + numRuns + " runs");

      GitHubActionPerformanceTest.ComparisonResult result =
          GitHubActionPerformanceTest.runComparison(maxRegexps, baselineResults);

      LOG.info("Test completed successfully!");
      LOG.info("Performance status: " + result.getPerformanceResult().getStatus());
      LOG.info("Explanation: " + result.getPerformanceResult().getExplanation());

      System.out.println("✅ Basic performance test validation completed successfully");

    } catch (Exception e) {
      LOG.severe("Test failed: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}
