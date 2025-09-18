package no.rmz.rmatch.benchmarks.framework;

import java.util.List;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;

/**
 * Interface for managing test patterns in JMH benchmarks.
 *
 * <p>This interface provides a systematic way to organize and access test patterns for performance
 * benchmarking, with JMH integration support for benchmark lifecycle management.
 */
public interface PatternLibrary {

  /**
   * Retrieves patterns filtered by category.
   *
   * @param category The pattern category to filter by
   * @return List of patterns in the specified category
   */
  List<TestPattern> getPatternsByCategory(PatternCategory category);

  /**
   * Creates a new test pattern with associated metadata.
   *
   * @param regex The regular expression string
   * @param metadata Pattern metadata for benchmarking
   * @return A new TestPattern instance
   */
  TestPattern createPattern(String regex, PatternMetadata metadata);

  /**
   * Adds a custom pattern to the library.
   *
   * @param pattern The pattern to add
   */
  void addCustomPattern(TestPattern pattern);

  /**
   * Gets all patterns in the library.
   *
   * @return List of all patterns
   */
  List<TestPattern> getAllPatterns();

  /**
   * Gets a subset of patterns up to the specified limit.
   *
   * @param maxPatterns Maximum number of patterns to return
   * @return List of patterns (up to maxPatterns)
   */
  List<TestPattern> getPatterns(int maxPatterns);

  /**
   * JMH setup method for benchmark preparation.
   *
   * <p>This method is called once per trial to prepare the pattern library for benchmark execution.
   * Override this method to implement custom initialization logic.
   *
   * @param config Benchmark configuration (can be null for default setup)
   */
  @Setup(Level.Trial)
  default void prepareForBenchmark(final BenchmarkConfiguration config) {
    // Default implementation - subclasses can override for custom initialization
  }
}
