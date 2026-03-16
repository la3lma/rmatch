package no.rmz.rmatch.benchmarks.framework;

import java.util.List;

/**
 * Example demonstrating usage of the Extended Testing Framework.
 *
 * <p>This class shows how to use the framework components for custom benchmarking outside of JMH,
 * as well as basic framework usage patterns.
 */
public final class FrameworkUsageExample {

  /** Utility class - private constructor. */
  private FrameworkUsageExample() {}

  /**
   * Demonstrates basic pattern library usage.
   *
   * @return Example test results
   */
  public static TestResults demonstratePatternLibraryUsage() {
    // Create a pattern library
    final PatternLibrary library = new DefaultPatternLibrary();

    // Get patterns by category
    final List<TestPattern> simplePatterns = library.getPatternsByCategory(PatternCategory.SIMPLE);
    final List<TestPattern> complexPatterns =
        library.getPatternsByCategory(PatternCategory.COMPLEX);

    System.out.println("Simple patterns: " + simplePatterns.size());
    System.out.println("Complex patterns: " + complexPatterns.size());

    // Create custom patterns
    final PatternMetadata customMetadata =
        new PatternMetadata(
            "custom_test", "A custom test pattern", PatternCategory.SIMPLE, 2, false);
    final TestPattern customPattern = library.createPattern("test.*", customMetadata);

    // Add to library
    library.addCustomPattern(customPattern);

    // Create example results
    return new TestResults(
        "example_test",
        1000000L, // 1ms in nanoseconds
        1024 * 1024, // 1MB
        100, // matches
        10, // patterns
        1000.0 // ops/sec
        );
  }

  /**
   * Demonstrates configuration and test suite creation.
   *
   * @return Example test suite
   */
  public static TestSuite createExampleTestSuite() {
    final BenchmarkConfiguration config = BenchmarkConfiguration.defaultConfig();

    final List<TestScenario> scenarios =
        List.of(
            new TestScenario(
                "simple_patterns_test",
                PatternCategory.SIMPLE,
                10,
                "hello world test pattern match",
                config),
            new TestScenario(
                "complex_patterns_test",
                PatternCategory.COMPLEX,
                5,
                "test@example.com 2023-09-18 simple patterns",
                config));

    return new TestSuite(
        "example_suite", "Example test suite for demonstration", scenarios, config);
  }

  /**
   * Example main method showing framework usage.
   *
   * @param args Command line arguments (ignored)
   */
  public static void main(final String[] args) {
    System.out.println("=== Extended Testing Framework Usage Example ===");

    // Demonstrate pattern library
    final TestResults results = demonstratePatternLibraryUsage();
    System.out.println("Example results: " + results.toStructuredLog());

    // Demonstrate test suite creation
    final TestSuite suite = createExampleTestSuite();
    System.out.println("Created test suite: " + suite);
    System.out.println("Scenarios in suite: " + suite.getScenarios().size());

    // Show configuration
    final BenchmarkConfiguration config = suite.getDefaultConfig();
    System.out.println("Configuration: " + config);

    System.out.println("\nFramework is ready for JMH benchmark execution!");
    System.out.println("Use ExtendedTestFramework class with JMH to run performance tests.");
  }
}
