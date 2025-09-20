package no.rmz.rmatch.benchmarks.framework;

/**
 * Metadata associated with a test pattern for benchmark tracking and categorization.
 *
 * <p>This class captures essential information about test patterns to support systematic
 * performance analysis and JMH integration.
 */
public final class PatternMetadata {
  private final String name;
  private final String description;
  private final PatternCategory category;
  private final int expectedComplexity;
  private final boolean isPathological;

  /**
   * Creates pattern metadata.
   *
   * @param name Short name for the pattern
   * @param description Human-readable description
   * @param category Pattern category for organization
   * @param expectedComplexity Expected computational complexity (1-10 scale)
   * @param isPathological Whether this pattern is known to be problematic
   */
  public PatternMetadata(
      final String name,
      final String description,
      final PatternCategory category,
      final int expectedComplexity,
      final boolean isPathological) {
    this.name = name;
    this.description = description;
    this.category = category;
    this.expectedComplexity = expectedComplexity;
    this.isPathological = isPathological;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public PatternCategory getCategory() {
    return category;
  }

  public int getExpectedComplexity() {
    return expectedComplexity;
  }

  public boolean isPathological() {
    return isPathological;
  }

  @Override
  public String toString() {
    return String.format(
        "PatternMetadata{name='%s', category=%s, complexity=%d, pathological=%s}",
        name, category, expectedComplexity, isPathological);
  }
}
