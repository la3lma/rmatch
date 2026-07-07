package no.rmz.rmatch.interfaces;

/** An epsilon-like NDFA edge guarded by a zero-width assertion. */
public record AssertionEdge(ZeroWidthAssertion assertion, NDFANode destination) {
  public boolean isSatisfiedBy(final MatchContext context) {
    return assertion.matches(context);
  }
}
