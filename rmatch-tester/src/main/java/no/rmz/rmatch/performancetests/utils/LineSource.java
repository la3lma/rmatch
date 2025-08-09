package no.rmz.rmatch.performancetests.utils;

public class LineSource {
  /** The line currently being processed. */
  private String currentLine;

  /** The index into some outer entity, where the start of the current line was gotten from. */
  private int currentStartOfLine = 0;

  /**
   * We need a method to access the currentLine since it isn't final, and the access is being made
   * from inside an anonymous class definition.
   *
   * @return the content of the currentLine.
   */
  public String getCurrentLine() {
    return currentLine;
  }

  public void setCurrentLine(final String currentLine) {
    setCurrentLine(currentLine, currentStartOfLine + currentLine.length());
  }

  public void setCurrentLine(String currentLine, final int currentStart) {
    this.currentLine = currentLine;
    this.currentStartOfLine = currentStart;
  }

  int getStartOfLineIndex() {
    return currentStartOfLine;
  }
}
