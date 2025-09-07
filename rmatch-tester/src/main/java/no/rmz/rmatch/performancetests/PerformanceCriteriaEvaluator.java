package no.rmz.rmatch.performancetests;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

/**
 * Evaluates performance test results against predefined criteria to determine pass/fail status.
 * Implements the improvement thresholds and regression detection logic as specified in the PRD.
 */
public final class PerformanceCriteriaEvaluator {

  /** Execution time improvement threshold: ≥5% faster than baseline */
  private static final double TIME_IMPROVEMENT_THRESHOLD = 0.05;

  /** Memory usage improvement threshold: ≥3% less memory than baseline */
  private static final double MEMORY_IMPROVEMENT_THRESHOLD = 0.03;

  /** Regression detection: Flag if performance degrades by ≥2% */
  private static final double REGRESSION_THRESHOLD = 0.02;

  /** Performance test result with criteria evaluation */
  public static class PerformanceResult {
    private final double timeImprovementPercent;
    private final double memoryImprovementPercent;
    private final Status status;
    private final String explanation;
    private final boolean statisticallySignificant;

    public PerformanceResult(
        double timeImprovementPercent,
        double memoryImprovementPercent,
        Status status,
        String explanation,
        boolean statisticallySignificant) {
      this.timeImprovementPercent = timeImprovementPercent;
      this.memoryImprovementPercent = memoryImprovementPercent;
      this.status = status;
      this.explanation = explanation;
      this.statisticallySignificant = statisticallySignificant;
    }

    public double getTimeImprovementPercent() {
      return timeImprovementPercent;
    }

    public double getMemoryImprovementPercent() {
      return memoryImprovementPercent;
    }

    public Status getStatus() {
      return status;
    }

    public String getExplanation() {
      return explanation;
    }

    public boolean isStatisticallySignificant() {
      return statisticallySignificant;
    }
  }

  /** Performance test status */
  public enum Status {
    PASS("✅ PASS"),
    FAIL("❌ FAIL"),
    WARNING("⚠️ WARNING");

    private final String displayName;

    Status(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }

  /** Statistics for multiple test runs */
  public static class RunStatistics {
    private final double mean;
    private final double stdDev;
    private final int count;

    public RunStatistics(double mean, double stdDev, int count) {
      this.mean = mean;
      this.stdDev = stdDev;
      this.count = count;
    }

    public double getMean() {
      return mean;
    }

    public double getStdDev() {
      return stdDev;
    }

    public int getCount() {
      return count;
    }
  }

  /** Private constructor for utility class */
  private PerformanceCriteriaEvaluator() {}

  /**
   * Evaluate performance results against baseline.
   *
   * @param currentResults List of current test run results (minimum 3 required)
   * @param baselineResults List of baseline test run results
   * @return PerformanceResult with pass/fail determination
   */
  public static PerformanceResult evaluate(
      List<? extends TestRunResult> currentResults, List<? extends TestRunResult> baselineResults) {

    checkNotNull(currentResults, "Current results cannot be null");
    checkNotNull(baselineResults, "Baseline results cannot be null");

    if (currentResults.size() < 3) {
      return new PerformanceResult(
          0,
          0,
          Status.FAIL,
          "Insufficient runs: need minimum 3, got " + currentResults.size(),
          false);
    }

    if (baselineResults.isEmpty()) {
      return new PerformanceResult(
          0, 0, Status.PASS, "No baseline data available - this run establishes the initial baseline", false);
    }

    // Calculate statistics for current and baseline runs
    RunStatistics currentTimeStats = calculateTimeStatistics(currentResults);
    RunStatistics currentMemoryStats = calculateMemoryStatistics(currentResults);
    RunStatistics baselineTimeStats = calculateTimeStatistics(baselineResults);
    RunStatistics baselineMemoryStats = calculateMemoryStatistics(baselineResults);

    // Calculate improvement percentages (negative means regression)
    double timeImprovementPercent =
        (baselineTimeStats.getMean() - currentTimeStats.getMean()) / baselineTimeStats.getMean();
    double memoryImprovementPercent =
        (baselineMemoryStats.getMean() - currentMemoryStats.getMean())
            / baselineMemoryStats.getMean();

    // Check for statistical significance using coefficient of variation
    boolean statisticallySignificant =
        isStatisticallySignificant(currentTimeStats, baselineTimeStats);

    return determineStatus(
        timeImprovementPercent, memoryImprovementPercent, statisticallySignificant);
  }

  /** Interface for test results that provide time and memory metrics */
  public interface TestRunResult {
    long getDurationInMillis();

    long getUsedMemoryInMb();
  }

  private static RunStatistics calculateTimeStatistics(List<? extends TestRunResult> results) {
    double sum = 0;
    for (TestRunResult result : results) {
      sum += result.getDurationInMillis();
    }
    double mean = sum / results.size();

    double variance = 0;
    for (TestRunResult result : results) {
      double diff = result.getDurationInMillis() - mean;
      variance += diff * diff;
    }
    double stdDev = Math.sqrt(variance / results.size());

    return new RunStatistics(mean, stdDev, results.size());
  }

  private static RunStatistics calculateMemoryStatistics(List<? extends TestRunResult> results) {
    double sum = 0;
    for (TestRunResult result : results) {
      sum += result.getUsedMemoryInMb();
    }
    double mean = sum / results.size();

    double variance = 0;
    for (TestRunResult result : results) {
      double diff = result.getUsedMemoryInMb() - mean;
      variance += diff * diff;
    }
    double stdDev = Math.sqrt(variance / results.size());

    return new RunStatistics(mean, stdDev, results.size());
  }

  private static boolean isStatisticallySignificant(RunStatistics current, RunStatistics baseline) {
    // Simple significance test: coefficient of variation should be reasonable
    double currentCV = current.getStdDev() / current.getMean();
    double baselineCV = baseline.getStdDev() / baseline.getMean();

    // Consider significant if coefficient of variation is < 20%
    return currentCV < 0.20 && baselineCV < 0.20;
  }

  private static PerformanceResult determineStatus(
      double timeImprovementPercent,
      double memoryImprovementPercent,
      boolean statisticallySignificant) {
    // Check for regressions first
    if (timeImprovementPercent < -REGRESSION_THRESHOLD
        || memoryImprovementPercent < -REGRESSION_THRESHOLD) {
      String explanation =
          String.format(
              "Performance regression detected: time %.1f%%, memory %.1f%%",
              timeImprovementPercent * 100, memoryImprovementPercent * 100);
      return new PerformanceResult(
          timeImprovementPercent,
          memoryImprovementPercent,
          Status.FAIL,
          explanation,
          statisticallySignificant);
    }

    // Check for meaningful improvements
    boolean timeImproved = timeImprovementPercent >= TIME_IMPROVEMENT_THRESHOLD;
    boolean memoryImproved = memoryImprovementPercent >= MEMORY_IMPROVEMENT_THRESHOLD;

    if (timeImproved || memoryImproved) {
      String explanation =
          String.format(
              "Performance improvement: time %.1f%%, memory %.1f%%",
              timeImprovementPercent * 100, memoryImprovementPercent * 100);
      return new PerformanceResult(
          timeImprovementPercent,
          memoryImprovementPercent,
          Status.PASS,
          explanation,
          statisticallySignificant);
    }

    // Performance within acceptable bounds
    if (!statisticallySignificant) {
      String explanation =
          String.format(
              "Performance change within noise threshold: time %.1f%%, memory %.1f%% (low statistical significance)",
              timeImprovementPercent * 100, memoryImprovementPercent * 100);
      return new PerformanceResult(
          timeImprovementPercent,
          memoryImprovementPercent,
          Status.WARNING,
          explanation,
          statisticallySignificant);
    }

    String explanation =
        String.format(
            "Performance maintained within acceptable bounds: time %.1f%%, memory %.1f%%",
            timeImprovementPercent * 100, memoryImprovementPercent * 100);
    return new PerformanceResult(
        timeImprovementPercent,
        memoryImprovementPercent,
        Status.PASS,
        explanation,
        statisticallySignificant);
  }
}
