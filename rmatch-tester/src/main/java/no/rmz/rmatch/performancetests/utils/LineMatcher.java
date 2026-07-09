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

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import no.rmz.rmatch.Action;
import no.rmz.rmatch.Buffer;

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
    long pos = 0;
    while (b.hasCharAt(pos)) {
      final StringBuilder line = new StringBuilder();
      while (b.hasCharAt(pos)) {
        final char ch = b.charAt(pos++);
        if (isEol(ch)) {
          break;
        }
        line.append(ch);
      }
      lineSource.setCurrentLine(line.toString());
      noOfLines += 1;
      matchDetector.detectMatchesForCurrentLine();
    }
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
