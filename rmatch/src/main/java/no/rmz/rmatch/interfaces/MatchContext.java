package no.rmz.rmatch.interfaces;

/** Positional context for zero-width assertions while consuming one input character. */
public record MatchContext(boolean atLineStart, boolean atLineEnd) {
  /** Context used by assertion-free code paths. */
  public static final MatchContext NONE = new MatchContext(false, false);

  /**
   * Build context for the character currently being consumed.
   *
   * @param currentPos zero-based current input position
   * @param previousChar character before the current position, or {@code null} at BOF
   * @param nextChar character after the current position, or {@code null} at EOF
   * @return line-anchor context for this position
   */
  public static MatchContext forPosition(
      final int currentPos, final Character previousChar, final Character nextChar) {
    return new MatchContext(
        currentPos == 0 || previousChar != null && previousChar == '\n',
        nextChar == null || nextChar == '\n');
  }
}
