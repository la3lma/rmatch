package no.rmz.rmatch.benchmarks.framework;

/**
 * Configuration parameters for benchmark execution.
 *
 * <p>This class encapsulates all configuration needed for JMH benchmark execution, providing a
 * clean interface for parameterizing performance tests.
 */
public final class BenchmarkConfiguration {
  private final int maxPatterns;
  private final int maxInputLength;
  private final boolean enablePercentileMetrics;
  private final boolean githubActionsMode;
  private final String testName;

  /**
   * Creates a benchmark configuration.
   *
   * @param maxPatterns Maximum number of patterns to use in benchmarks
   * @param maxInputLength Maximum length of test input strings
   * @param enablePercentileMetrics Whether to collect P95/P99 latency metrics
   * @param githubActionsMode Whether running in GitHub Actions environment
   * @param testName Name of the test for reporting
   */
  public BenchmarkConfiguration(
      final int maxPatterns,
      final int maxInputLength,
      final boolean enablePercentileMetrics,
      final boolean githubActionsMode,
      final String testName) {
    this.maxPatterns = maxPatterns;
    this.maxInputLength = maxInputLength;
    this.enablePercentileMetrics = enablePercentileMetrics;
    this.githubActionsMode = githubActionsMode;
    this.testName = testName;
  }

  public int getMaxPatterns() {
    return maxPatterns;
  }

  public int getMaxInputLength() {
    return maxInputLength;
  }

  public boolean isEnablePercentileMetrics() {
    return enablePercentileMetrics;
  }

  public boolean isGithubActionsMode() {
    return githubActionsMode;
  }

  public String getTestName() {
    return testName;
  }

  /**
   * Creates a default configuration suitable for CI environments.
   *
   * @return Default configuration
   */
  public static BenchmarkConfiguration defaultConfig() {
    return new BenchmarkConfiguration(
        1000, // Max patterns
        100000, // Max input length
        true, // Enable percentiles
        System.getenv("GITHUB_ACTIONS") != null, // Detect GitHub Actions
        "default_benchmark");
  }

  @Override
  public String toString() {
    return String.format(
        "BenchmarkConfiguration{maxPatterns=%d, maxInputLength=%d, percentiles=%s, github=%s, name='%s'}",
        maxPatterns, maxInputLength, enablePercentileMetrics, githubActionsMode, testName);
  }
}
