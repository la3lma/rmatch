package no.rmz.rmatch.performancetests;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;
import no.rmz.rmatch.performancetests.utils.WutheringHeightsBuffer;

/**
 * Performance test runner designed for GitHub Actions that orchestrates comparison between rmatch
 * and JavaRegexpMatcher implementations.
 */
public final class GitHubActionPerformanceTest {

  private static final Logger LOG = Logger.getLogger(GitHubActionPerformanceTest.class.getName());

  /** Minimum number of test runs for statistical significance */
  private static final int MIN_RUNS = 3;

  /** Default number of test runs */
  private static final int DEFAULT_RUNS = 5;

  /** Performance comparison result */
  public static class ComparisonResult {
    private final List<MatcherBenchmarker.TestRunResult> rmatchResults;
    private final List<MatcherBenchmarker.TestRunResult> javaResults;
    private final List<MatcherBenchmarker.TestRunResult> baselineRmatchResults;
    private final List<MatcherBenchmarker.TestRunResult> baselineJavaResults;
    private final PerformanceCriteriaEvaluator.PerformanceResult performanceResult;

    public ComparisonResult(
        List<MatcherBenchmarker.TestRunResult> rmatchResults,
        List<MatcherBenchmarker.TestRunResult> javaResults,
        List<MatcherBenchmarker.TestRunResult> baselineRmatchResults,
        List<MatcherBenchmarker.TestRunResult> baselineJavaResults,
        PerformanceCriteriaEvaluator.PerformanceResult performanceResult) {
      this.rmatchResults = rmatchResults;
      this.javaResults = javaResults;
      this.baselineRmatchResults = baselineRmatchResults;
      this.baselineJavaResults = baselineJavaResults;
      this.performanceResult = performanceResult;
    }

    public List<MatcherBenchmarker.TestRunResult> getRmatchResults() {
      return rmatchResults;
    }

    public List<MatcherBenchmarker.TestRunResult> getJavaResults() {
      return javaResults;
    }

    public List<MatcherBenchmarker.TestRunResult> getBaselineRmatchResults() {
      return baselineRmatchResults;
    }

    public List<MatcherBenchmarker.TestRunResult> getBaselineJavaResults() {
      return baselineJavaResults;
    }

    public PerformanceCriteriaEvaluator.PerformanceResult getPerformanceResult() {
      return performanceResult;
    }
  }

  /** Private constructor for utility class */
  private GitHubActionPerformanceTest() {}

  /**
   * Run performance comparison between rmatch and JavaRegexpMatcher against baseline.
   *
   * @param corpusPath Path to the test corpus
   * @param regexpPath Path to the regular expressions file
   * @param maxRegexps Maximum number of regular expressions to test
   * @param numRuns Number of test runs (minimum 3 for statistical significance)
   * @param baselineResults Baseline results to compare against (can be empty)
   * @return ComparisonResult with performance analysis
   */
  public static ComparisonResult runComparison(
      String corpusPath,
      String regexpPath,
      int maxRegexps,
      int numRuns,
      List<MatcherBenchmarker.TestRunResult> baselineResults) {

    checkNotNull(corpusPath, "Corpus path cannot be null");
    checkNotNull(regexpPath, "Regexp path cannot be null");

    if (numRuns < MIN_RUNS) {
      throw new IllegalArgumentException("Minimum " + MIN_RUNS + " runs required, got " + numRuns);
    }

    LOG.info("Starting performance comparison with " + numRuns + " runs");
    LOG.info("Corpus: " + corpusPath + ", Regexps: " + regexpPath + ", Max regexps: " + maxRegexps);
    
    int availableCores = MatcherFactory.getAvailableProcessors();
    int rmatchThreads = MatcherFactory.getDefaultPartitionCount();
    LOG.info("System cores: " + availableCores + ", rmatch threads: " + rmatchThreads);

    try {
      // Load test data
      Buffer buffer = new WutheringHeightsBuffer(corpusPath);
      List<String> regexps = MatcherBenchmarker.loadRegexpsFromFile(regexpPath, maxRegexps);

      // Run multiple test iterations for statistical significance
      List<MatcherBenchmarker.TestRunResult> rmatchResults = new ArrayList<>();
      List<MatcherBenchmarker.TestRunResult> javaResults = new ArrayList<>();

      for (int run = 1; run <= numRuns; run++) {
        LOG.info("Executing run " + run + "/" + numRuns);

        // Test rmatch implementation
        Matcher rmatcher = MatcherFactory.newMatcher();
        MatcherBenchmarker.TestRunResult rmatchResult =
            MatcherBenchmarker.testACorpusNG("rmatch", rmatcher, regexps, buffer);
        rmatchResults.add(rmatchResult);

        // Test Java regexp implementation
        Matcher javaMatcher = new JavaRegexpMatcher();
        MatcherBenchmarker.TestRunResult javaResult =
            MatcherBenchmarker.testACorpusNG("java", javaMatcher, regexps, buffer);
        javaResults.add(javaResult);

        LOG.info(
            "Run "
                + run
                + " completed - rmatch: "
                + rmatchResult.durationInMillis()
                + "ms, java: "
                + javaResult.durationInMillis()
                + "ms, cores: "
                + availableCores
                + ", threads: "
                + rmatchThreads);
      }

      // Separate baseline results by matcher type
      List<MatcherBenchmarker.TestRunResult> baselineRmatchResults = new ArrayList<>();
      List<MatcherBenchmarker.TestRunResult> baselineJavaResults = new ArrayList<>();

      for (MatcherBenchmarker.TestRunResult baseline : baselineResults) {
        if ("rmatch".equals(baseline.matcherTypeName())) {
          baselineRmatchResults.add(baseline);
        } else if ("java".equals(baseline.matcherTypeName())) {
          baselineJavaResults.add(baseline);
        }
      }

      // Evaluate performance against criteria (compare rmatch current vs rmatch baseline)
      PerformanceCriteriaEvaluator.PerformanceResult performanceResult =
          PerformanceCriteriaEvaluator.evaluate(rmatchResults, baselineRmatchResults);

      return new ComparisonResult(
          rmatchResults,
          javaResults,
          baselineRmatchResults,
          baselineJavaResults,
          performanceResult);

    } catch (Exception e) {
      LOG.severe("Performance comparison failed: " + e.getMessage());
      throw new RuntimeException("Performance comparison failed", e);
    }
  }

  /**
   * Run performance comparison with default settings.
   *
   * @param maxRegexps Maximum number of regular expressions to test
   * @param baselineResults Baseline results to compare against
   * @return ComparisonResult with performance analysis
   */
  public static ComparisonResult runComparison(
      int maxRegexps, List<MatcherBenchmarker.TestRunResult> baselineResults) {
    return runComparison(
        "rmatch-tester/corpus/wuthr10.txt",
        "rmatch-tester/corpus/real-words-in-wuthering-heights.txt",
        maxRegexps,
        DEFAULT_RUNS,
        baselineResults);
  }
}
