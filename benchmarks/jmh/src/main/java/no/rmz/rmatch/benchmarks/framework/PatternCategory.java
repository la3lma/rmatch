package no.rmz.rmatch.benchmarks.framework;

/**
 * Categories for organizing test patterns by complexity and characteristics.
 *
 * <p>This categorization supports systematic performance testing across different pattern types,
 * enabling identification of performance characteristics and regressions.
 */
public enum PatternCategory {
  /** Simple literal patterns and basic character classes. */
  SIMPLE("Simple patterns like literals and basic character classes"),

  /** Complex patterns with quantifiers, alternations, and groups. */
  COMPLEX("Complex patterns with quantifiers, alternations, and nested groups"),

  /** Pathological patterns that may cause exponential backtracking or state explosion. */
  PATHOLOGICAL("Patterns known to cause performance issues or edge cases");

  private final String description;

  PatternCategory(final String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
