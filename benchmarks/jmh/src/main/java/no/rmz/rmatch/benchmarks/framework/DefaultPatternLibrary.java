package no.rmz.rmatch.benchmarks.framework;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Default implementation of PatternLibrary with common regex patterns for testing.
 *
 * <p>This implementation provides a comprehensive set of test patterns organized by category,
 * suitable for performance benchmarking and regression testing.
 */
public final class DefaultPatternLibrary implements PatternLibrary {
  private final List<TestPattern> patterns;

  /** Creates a default pattern library with predefined patterns. */
  public DefaultPatternLibrary() {
    this.patterns = new ArrayList<>();
    initializeDefaultPatterns();
  }

  @Override
  public List<TestPattern> getPatternsByCategory(final PatternCategory category) {
    return patterns.stream().filter(p -> p.getMetadata().getCategory() == category).toList();
  }

  @Override
  public TestPattern createPattern(final String regex, final PatternMetadata metadata) {
    return new TestPattern(regex, metadata);
  }

  @Override
  public void addCustomPattern(final TestPattern pattern) {
    patterns.add(pattern);
  }

  @Override
  public List<TestPattern> getAllPatterns() {
    return List.copyOf(patterns);
  }

  @Override
  public List<TestPattern> getPatterns(final int maxPatterns) {
    return patterns.stream().limit(maxPatterns).toList();
  }

  /** Initialize the library with a comprehensive set of test patterns. */
  private void initializeDefaultPatterns() {
    // Simple patterns
    addSimplePatterns();

    // Complex patterns
    addComplexPatterns();

    // Pathological patterns - using safer patterns to avoid regex issues
    addPathologicalPatterns();
  }

  private void addSimplePatterns() {
    final List<TestPattern> simplePatterns =
        List.of(
            // Literal patterns
            TestPattern.simple("literal_hello", "hello", PatternCategory.SIMPLE),
            TestPattern.simple("literal_world", "world", PatternCategory.SIMPLE),
            TestPattern.simple("literal_test", "test", PatternCategory.SIMPLE),
            TestPattern.simple("literal_pattern", "pattern", PatternCategory.SIMPLE),
            TestPattern.simple("literal_match", "match", PatternCategory.SIMPLE),
            TestPattern.simple("literal_the", "the", PatternCategory.SIMPLE),
            TestPattern.simple("literal_and", "and", PatternCategory.SIMPLE),
            TestPattern.simple("literal_email", "email", PatternCategory.SIMPLE),
            TestPattern.simple("literal_phone", "phone", PatternCategory.SIMPLE),
            TestPattern.simple("literal_date", "date", PatternCategory.SIMPLE),

            // Simple character sets
            TestPattern.simple("vowels", "[aeiou]", PatternCategory.SIMPLE),
            TestPattern.simple("consonants", "[bcdfghjklmnpqrstvwxyz]", PatternCategory.SIMPLE),
            TestPattern.simple("digits", "[0-9]", PatternCategory.SIMPLE),
            TestPattern.simple("hex_digits", "[0-9a-fA-F]", PatternCategory.SIMPLE),
            TestPattern.simple("lowercase", "[a-z]", PatternCategory.SIMPLE),
            TestPattern.simple("uppercase", "[A-Z]", PatternCategory.SIMPLE),
            TestPattern.simple("word_chars", "[a-zA-Z0-9_]", PatternCategory.SIMPLE),

            // Simple quantifiers
            TestPattern.simple("one_or_more_a", "a+", PatternCategory.SIMPLE),
            TestPattern.simple("zero_or_more_b", "b*", PatternCategory.SIMPLE),
            TestPattern.simple("optional_c", "c?", PatternCategory.SIMPLE),
            TestPattern.simple("exactly_three_d", "d{3}", PatternCategory.SIMPLE),
            TestPattern.simple("two_to_five_e", "e{2,5}", PatternCategory.SIMPLE));

    patterns.addAll(simplePatterns);
  }

  private void addComplexPatterns() {
    final List<TestPattern> complexPatterns =
        List.of(
            // Email-like patterns (simplified for rmatch compatibility)
            new TestPattern(
                "[a-zA-Z0-9]+@[a-zA-Z0-9]+[.][a-zA-Z]+",
                new PatternMetadata(
                    "simple_email", "Simple email pattern", PatternCategory.COMPLEX, 5, false)),

            // Phone number patterns (simplified)
            new TestPattern(
                "[0-9]{3}-[0-9]{3}-[0-9]{4}",
                new PatternMetadata(
                    "phone_pattern", "Phone number pattern", PatternCategory.COMPLEX, 4, false)),

            // Date patterns
            new TestPattern(
                "[0-9]{4}-[0-9]{2}-[0-9]{2}",
                new PatternMetadata(
                    "iso_date", "ISO date pattern", PatternCategory.COMPLEX, 3, false)),
            new TestPattern(
                "[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}",
                new PatternMetadata(
                    "us_date", "US date pattern", PatternCategory.COMPLEX, 3, false)),

            // Complex alternations
            new TestPattern(
                "(cat|dog|bird|fish|hamster)",
                new PatternMetadata(
                    "animals", "Animal alternatives", PatternCategory.COMPLEX, 3, false)),
            new TestPattern(
                "(red|green|blue|yellow|orange|purple|pink|brown|black|white)",
                new PatternMetadata(
                    "colors", "Color alternatives", PatternCategory.COMPLEX, 4, false)),

            // Nested groups and quantifiers
            new TestPattern(
                "([a-z]+[A-Z]+){2,5}",
                new PatternMetadata(
                    "mixed_case_sequences",
                    "Mixed case sequences",
                    PatternCategory.COMPLEX,
                    5,
                    false)),
            new TestPattern(
                "[0-9]+[.][0-9]+[.][0-9]+[.][0-9]+",
                new PatternMetadata(
                    "ip_address", "IP address pattern", PatternCategory.COMPLEX, 4, false)),

            // Patterns without anchors (rmatch compatibility)
            new TestPattern(
                "[a-z]+",
                new PatternMetadata(
                    "lowercase_letters",
                    "Lowercase letters",
                    PatternCategory.COMPLEX,
                    3,
                    false)),
            new TestPattern(
                "[A-Z][a-z]*",
                new PatternMetadata(
                    "capitalized_word", "Capitalized word", PatternCategory.COMPLEX, 3, false)));

    patterns.addAll(complexPatterns);
  }

  private void addPathologicalPatterns() {
    final List<TestPattern> pathologicalPatterns =
        List.of(
            // Exponential backtracking patterns (simplified to avoid escape issues)
            new TestPattern(
                "(a+)+b",
                new PatternMetadata(
                    "exponential_nested",
                    "Nested quantifiers causing exponential backtracking",
                    PatternCategory.PATHOLOGICAL,
                    10,
                    true)),
            new TestPattern(
                "(a|a)*b",
                new PatternMetadata(
                    "exponential_alternation",
                    "Ambiguous alternation causing backtracking",
                    PatternCategory.PATHOLOGICAL,
                    10,
                    true)),

            // Large state explosion patterns
            new TestPattern(
                "a{100,200}",
                new PatternMetadata(
                    "large_quantifier",
                    "Large quantifier range",
                    PatternCategory.PATHOLOGICAL,
                    8,
                    true)),

            // Deeply nested groups
            new TestPattern(
                "((((a+)+)+)+)+",
                new PatternMetadata(
                    "deep_nesting", "Deeply nested groups", PatternCategory.PATHOLOGICAL, 9, true)),

            // Complex alternations with potential for slow matching
            new TestPattern(
                "(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z)+",
                new PatternMetadata(
                    "large_alternation",
                    "Large alternation with quantifier",
                    PatternCategory.PATHOLOGICAL,
                    7,
                    true)));

    patterns.addAll(pathologicalPatterns);
  }

  /**
   * Get a balanced subset of patterns from all categories.
   *
   * @param maxPatterns Maximum number of patterns to return
   * @return Balanced selection of patterns
   */
  public List<TestPattern> getBalancedPatterns(final int maxPatterns) {
    final int perCategory = maxPatterns / 3;
    final int remainder = maxPatterns % 3;

    return Stream.of(
            getPatternsByCategory(PatternCategory.SIMPLE).stream().limit(perCategory + remainder),
            getPatternsByCategory(PatternCategory.COMPLEX).stream().limit(perCategory),
            getPatternsByCategory(PatternCategory.PATHOLOGICAL).stream().limit(perCategory))
        .flatMap(s -> s)
        .toList();
  }
}
