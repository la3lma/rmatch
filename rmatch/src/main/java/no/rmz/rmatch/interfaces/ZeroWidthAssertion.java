package no.rmz.rmatch.interfaces;

/** Zero-width assertions supported by the NDFA closure machinery. */
public enum ZeroWidthAssertion {
  /** Beginning of input, or the position immediately after a newline. */
  LINE_START {
    @Override
    public boolean matches(final MatchContext context) {
      return context.atLineStart();
    }
  },
  /** End of input, or the position immediately before a newline. */
  LINE_END {
    @Override
    public boolean matches(final MatchContext context) {
      return context.atLineEnd();
    }
  };

  /**
   * Return whether this assertion is satisfied by the supplied input context.
   *
   * @param context current positional context
   * @return {@code true} when this assertion holds
   */
  public abstract boolean matches(MatchContext context);
}
