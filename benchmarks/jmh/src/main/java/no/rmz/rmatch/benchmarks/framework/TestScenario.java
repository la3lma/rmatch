package no.rmz.rmatch.benchmarks.framework;

/**
 * Individual benchmark test scenario within a test suite.
 *
 * <p>Represents a single test case with specific parameters for JMH benchmark execution.
 */
public final class TestScenario {
  private final String name;
  private final PatternCategory category;
  private final int patternCount;
  private final String inputData;
  private final BenchmarkConfiguration config;

  /**
   * Creates a test scenario.
   *
   * @param name Scenario name
   * @param category Pattern category to test
   * @param patternCount Number of patterns to use
   * @param inputData Test input data
   * @param config Benchmark configuration
   */
  public TestScenario(
      final String name,
      final PatternCategory category,
      final int patternCount,
      final String inputData,
      final BenchmarkConfiguration config) {
    this.name = name;
    this.category = category;
    this.patternCount = patternCount;
    this.inputData = inputData;
    this.config = config;
  }

  public String getName() {
    return name;
  }

  public PatternCategory getCategory() {
    return category;
  }

  public int getPatternCount() {
    return patternCount;
  }

  public String getInputData() {
    return inputData;
  }

  public BenchmarkConfiguration getConfig() {
    return config;
  }

  @Override
  public String toString() {
    return String.format(
        "TestScenario{name='%s', category=%s, patterns=%d, inputLength=%d}",
        name, category, patternCount, inputData.length());
  }
}
