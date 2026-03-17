/**
 * Copyright 2026. Bjørn Remseth (rmz@rmz.no).
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
