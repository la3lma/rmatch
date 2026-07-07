package no.rmz.rmatch.interfaces;

/**
 * Epsilon-like NDFA edge guarded by a zero-width assertion.
 *
 * @param assertion condition that must hold before the edge may be followed
 * @param destination node reached when the assertion is satisfied
 */
public record AssertionEdge(ZeroWidthAssertion assertion, NDFANode destination) {
  /**
   * Return whether this edge may be followed in the supplied input context.
   *
   * @param context current positional context
   * @param afterCurrentChar whether the assertion is evaluated after consuming the current
   *     character
   * @return {@code true} if the assertion is satisfied
   */
  public boolean isSatisfiedBy(final MatchContext context, final boolean afterCurrentChar) {
    return assertion.matches(context, afterCurrentChar);
  }
}
