/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may get a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.rmz.rmatch.utils;

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import no.rmz.rmatch.Buffer;

/**
 * {@link Buffer} implementation backed by a {@link String}.
 *
 * <p>This is the standard buffer implementation for application code. The backing string is
 * immutable, so instances are thread-safe without synchronization and may be scanned concurrently
 * by partitioned matchers.
 */
public final class RegexStringBuffer implements Buffer {

  /** A string containing the entire content of the buffer. */
  private final String str;

  /**
   * Create a buffer over the supplied string.
   *
   * @param str input text to scan
   */
  public RegexStringBuffer(final String str) {
    this.str = checkNotNull(str);
  }

  @Override
  public boolean hasCharAt(final long pos) {
    return pos >= 0 && pos < str.length();
  }

  @Override
  public char charAt(final long pos) {
    return str.charAt(Math.toIntExact(pos));
  }

  /**
   * Return a substring from the backing string.
   *
   * <p>The {@code stop} argument is exclusive, just like {@link String#substring(int, int)}.
   *
   * @param start zero-based inclusive start offset
   * @param stop zero-based exclusive stop offset
   * @return text in the half-open range {@code [start, stop)}
   */
  @Override
  public String getString(final long start, final long stop) {
    return str.substring(Math.toIntExact(start), Math.toIntExact(stop));
  }

  /**
   * Return the total length of the backing string.
   *
   * @return number of characters in the backing string
   */
  public int getLength() {
    return str.length();
  }

  /**
   * Return the suffix from {@code start} to the end of the backing string.
   *
   * @param start zero-based inclusive start offset
   * @return text from {@code start} through the end of input
   */
  public String getCurrentRestString(final int start) {
    return str.substring(start);
  }

  @Override
  public String toString() {
    return "[RegexStringBuffer str = " + str + "]";
  }
}
