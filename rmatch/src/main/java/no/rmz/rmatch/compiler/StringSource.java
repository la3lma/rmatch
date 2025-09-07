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

import static com.google.common.base.Preconditions.*;

// XXX This looks really stupid!

/** A source of character used by the compiler to get input characters. */
public final class StringSource {

  /** The string we're getting the characters from. */
  private final String string;

  /** The current index into the string. */
  private int index;

  /** The length of the string. */
  private final int len;

  /**
   * Create a new instance of the StringSource.
   *
   * @param string ther string we will be getting characters from.
   */
  public StringSource(final String string) {
    this.string = checkNotNull(string);
    this.index = 0;
    this.len = string.length();
  }

  /**
   * Are there more characters?
   *
   * @return true iff more chars to read.
   */
  public boolean hasNext() {
    return index < len;
  }

  /**
   * Get next character and increment index.
   *
   * @return the next character.
   */
  public char next() {
    return string.charAt(index++);
  }

  /**
   * If more characters after thd current, then return then return the next character without
   * advancing the index pointer, otherwise return null.
   *
   * @return next character or null if there are no more characters to read.
   */
  public Character peek() {
    if (hasNext()) {
      return string.charAt(index);
    } else {
      return null;
    }
  }
}
