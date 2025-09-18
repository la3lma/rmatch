package no.rmz.rmatch.benchmarks.framework;

/**
 * Represents a test pattern with its associated metadata for benchmark execution.
 *
 * <p>This class encapsulates a regular expression pattern along with its metadata for use in JMH
 * benchmarks and performance testing frameworks.
 */
public final class TestPattern {
  private final String regex;
  private final PatternMetadata metadata;

  /**
   * Creates a test pattern.
   *
   * @param regex The regular expression string
   * @param metadata Associated metadata for benchmarking
   */
  public TestPattern(final String regex, final PatternMetadata metadata) {
    this.regex = regex;
    this.metadata = metadata;
  }

  public String getRegex() {
    return regex;
  }

  public PatternMetadata getMetadata() {
    return metadata;
  }

  /**
   * Creates a simple test pattern with minimal metadata.
   *
   * @param name Pattern name
   * @param regex Regular expression string
   * @param category Pattern category
   * @return TestPattern instance
   */
  public static TestPattern simple(
      final String name, final String regex, final PatternCategory category) {
    final PatternMetadata metadata = new PatternMetadata(name, name, category, 1, false);
    return new TestPattern(regex, metadata);
  }

  @Override
  public String toString() {
    return String.format("TestPattern{regex='%s', metadata=%s}", regex, metadata);
  }
}
