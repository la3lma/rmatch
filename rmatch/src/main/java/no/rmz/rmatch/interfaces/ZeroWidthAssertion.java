package no.rmz.rmatch.interfaces;

/** Zero-width assertions supported by the NDFA closure machinery. */
public enum ZeroWidthAssertion {
  LINE_START {
    @Override
    public boolean matches(final MatchContext context) {
      return context.atLineStart();
    }
  },
  LINE_END {
    @Override
    public boolean matches(final MatchContext context) {
      return context.atLineEnd();
    }
  };

  /** Return true when this assertion is satisfied by the supplied input context. */
  public abstract boolean matches(MatchContext context);
}
