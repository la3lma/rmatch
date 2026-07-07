package no.rmz.rmatch.interfaces;

/** Zero-width assertions supported by the NDFA closure machinery. */
public enum ZeroWidthAssertion {
  LINE_START {
    @Override
    public boolean matches(final MatchContext context, final boolean afterCurrentChar) {
      return !afterCurrentChar && context.atLineStart();
    }
  },
  LINE_END {
    @Override
    public boolean matches(final MatchContext context, final boolean afterCurrentChar) {
      return afterCurrentChar && context.atLineEnd();
    }
  },
  WORD_BOUNDARY {
    @Override
    public boolean matches(final MatchContext context, final boolean afterCurrentChar) {
      return afterCurrentChar ? context.atWordBoundaryAfter() : context.atWordBoundaryBefore();
    }
  },
  NON_WORD_BOUNDARY {
    @Override
    public boolean matches(final MatchContext context, final boolean afterCurrentChar) {
      return afterCurrentChar ? !context.atWordBoundaryAfter() : !context.atWordBoundaryBefore();
    }
  };

  /** Return true when this assertion is satisfied by the supplied input context. */
  public abstract boolean matches(MatchContext context, boolean afterCurrentChar);
}
