package no.rmz.rmatch.interfaces;

/** Zero-width assertions supported by the NDFA closure machinery. */
public enum ZeroWidthAssertion {
  /** Beginning of input, or the position immediately after a newline. */
  LINE_START {
    @Override
    public boolean matches(final MatchContext context, final boolean afterCurrentChar) {
      return !afterCurrentChar && context.atLineStart();
    }
  },
  /** End of input, or the position immediately before a newline. */
  LINE_END {
    @Override
    public boolean matches(final MatchContext context, final boolean afterCurrentChar) {
      return afterCurrentChar && context.atLineEnd();
    }
  },
  /** Position where one side is an ASCII word character and the other side is not. */
  WORD_BOUNDARY {
    @Override
    public boolean matches(final MatchContext context, final boolean afterCurrentChar) {
      return afterCurrentChar ? context.atWordBoundaryAfter() : context.atWordBoundaryBefore();
    }
  },
  /** Position where both sides are either ASCII word characters or non-word characters. */
  NON_WORD_BOUNDARY {
    @Override
    public boolean matches(final MatchContext context, final boolean afterCurrentChar) {
      return afterCurrentChar ? !context.atWordBoundaryAfter() : !context.atWordBoundaryBefore();
    }
  };

  /**
   * Return whether this assertion is satisfied by the supplied input context.
   *
   * @param context current positional context
   * @param afterCurrentChar whether the assertion is evaluated after consuming the current
   *     character
   * @return {@code true} when this assertion holds
   */
  public abstract boolean matches(MatchContext context, boolean afterCurrentChar);
}
