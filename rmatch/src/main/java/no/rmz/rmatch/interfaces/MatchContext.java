package no.rmz.rmatch.interfaces;

/**
 * Positional context for zero-width assertions while consuming one input character.
 *
 * <p>Assertions such as {@code ^}, {@code $}, {@code \b}, and {@code \B} do not consume input. They
 * ask questions about the position around the character currently being consumed. This record
 * carries those answers so the NDFA closure machinery can keep assertion semantics inside the
 * automaton transition model instead of repairing matches after the fact.
 */
public record MatchContext(
    boolean atLineStart,
    boolean atLineEnd,
    boolean atWordBoundaryBefore,
    boolean atWordBoundaryAfter) {
  /** Context used by assertion-free code paths. */
  public static final MatchContext NONE = new MatchContext(false, false, false, false);

  /**
   * Build context for the character currently being consumed.
   *
   * @param currentPos zero-based current input position
   * @param previousChar character before the current position, or {@code null} at BOF
   * @param currentChar character currently being consumed
   * @param nextChar character after the current position, or {@code null} at EOF
   * @return assertion context for this position
   */
  public static MatchContext forPosition(
      final int currentPos,
      final Character previousChar,
      final Character currentChar,
      final Character nextChar) {
    return new MatchContext(
        currentPos == 0 || previousChar != null && previousChar == '\n',
        nextChar == null || nextChar == '\n',
        isWordChar(previousChar) != isWordChar(currentChar),
        isWordChar(currentChar) != isWordChar(nextChar));
  }

  /** rmatch word-boundary semantics are deliberately aligned with its ASCII {@code \w} class. */
  private static boolean isWordChar(final Character ch) {
    return ch != null
        && (ch >= 'a' && ch <= 'z'
            || ch >= 'A' && ch <= 'Z'
            || ch >= '0' && ch <= '9'
            || ch == '_');
  }
}
