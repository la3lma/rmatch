/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
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
package no.rmz.rmatch.compiler;

import static no.rmz.rmatch.internal.Checks.checkNotNull;

/** Cursor over a pattern string used by the surface parser. */
final class StringSource {

  /** Pattern text being read. */
  private final String string;

  /** The current index into the string. */
  private int index;

  /** The length of the string. */
  private final int len;

  /**
   * Create a parser source over a pattern string.
   *
   * @param string pattern text to read
   */
  public StringSource(final String string) {
    this.string = checkNotNull(string);
    this.index = 0;
    this.len = string.length();
  }

  /**
   * Return whether more characters can be read.
   *
   * @return {@code true} if another character is available
   */
  public boolean hasNext() {
    return index < len;
  }

  /**
   * Return the next character and advance the source position.
   *
   * @return next character
   */
  public char next() {
    return string.charAt(index++);
  }

  /**
   * Return the index of the next character to be read.
   *
   * @return zero-based source position
   */
  public int getIndex() {
    return index;
  }

  /**
   * Return the next character without consuming it.
   *
   * @return next character, or {@code null} if no more characters are available
   */
  public Character peek() {
    if (hasNext()) {
      return string.charAt(index);
    } else {
      return null;
    }
  }
}
