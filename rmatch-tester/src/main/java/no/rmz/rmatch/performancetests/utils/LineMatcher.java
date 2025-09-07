package no.rmz.rmatch.performancetests.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;

public final class LineMatcher {
  private final LineSource lineSource;
  private final MatchDetector matchDetector;

  private int noOfLines = 0;

  public LineMatcher(final LineSource lineSource, final MatchDetector matchDetector) {
    this.lineSource = checkNotNull(lineSource);
    this.matchDetector = checkNotNull(matchDetector);
  }

  public int getNoOfLines() {
    return noOfLines;
  }

  public void match(final Buffer b) {
    checkNotNull(b);
    while (b.hasNext()) {
      lineSource.setCurrentLine(readLine(b));
      noOfLines += 1;
      matchDetector.detectMatchesForCurrentLine();
    }
  }

  /**
   * Read a new line. We will assume that the input buffer has at least one character available.
   *
   * @param b a source of characters
   * @return a line of text.
   */
  private String readLine(final Buffer b) {
    checkNotNull(b);
    final StringBuilder sb = new StringBuilder();
    do {
      final Character ch = b.getNext();
      if (isEol(ch)) {
        return sb.toString();
      }
      sb.append(ch);
    } while (b.hasNext());

    return sb.toString();
  }

  /**
   * True iff a character is an EOL character.
   *
   * @param ch the input character.
   * @return true iff the input character is an EOL character
   */
  private boolean isEol(final Character ch) {
    return ch != null && ch == '\n';
  }

  void add(final String rexpString, final Action actionToRun) {
    checkNotNull(rexpString);
    checkNotNull(actionToRun);
    matchDetector.add(rexpString, actionToRun);
  }
}
