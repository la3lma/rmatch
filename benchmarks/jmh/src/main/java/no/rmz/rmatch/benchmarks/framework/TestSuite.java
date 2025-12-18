package no.rmz.rmatch.benchmarks.framework;

import java.util.List;

/**
 * Collection of test scenarios for systematic performance testing.
 *
 * <p>Organizes multiple test scenarios for execution within JMH benchmarks, providing structured
 * testing across different pattern categories and input sizes.
 */
public final class TestSuite {
  private final String name;
  private final String description;
  private final List<TestScenario> scenarios;
  private final BenchmarkConfiguration defaultConfig;

  /**
   * Creates a test suite.
   *
   * @param name Suite name
   * @param description Suite description
   * @param scenarios List of test scenarios
   * @param defaultConfig Default configuration for scenarios
   */
  public TestSuite(
      final String name,
      final String description,
      final List<TestScenario> scenarios,
      final BenchmarkConfiguration defaultConfig) {
    this.name = name;
    this.description = description;
    this.scenarios = List.copyOf(scenarios); // Immutable copy
    this.defaultConfig = defaultConfig;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public List<TestScenario> getScenarios() {
    return scenarios;
  }

  public BenchmarkConfiguration getDefaultConfig() {
    return defaultConfig;
  }

  /**
   * Gets scenarios filtered by pattern category.
   *
   * @param category Pattern category to filter by
   * @return List of scenarios matching the category
   */
  public List<TestScenario> getScenariosByCategory(final PatternCategory category) {
    return scenarios.stream().filter(s -> s.getCategory() == category).toList();
  }

  @Override
  public String toString() {
    return String.format(
        "TestSuite{name='%s', description='%s', scenarios=%d}",
        name, description, scenarios.size());
  }
}
