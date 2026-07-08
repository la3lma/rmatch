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
package no.rmz.rmatch.interfaces;

/**
 * Input abstraction consumed by rmatch engines.
 *
 * <p>Most callers should use {@code RegexStringBuffer}, which adapts a {@link String} to this
 * interface. Custom buffer implementations are useful when input is backed by another data
 * structure, but they must preserve the cursor semantics described here.
 *
 * <p>A buffer starts before the first character. Each call to {@link #getNext()} advances the
 * cursor and returns the character at the new position. The current position is zero-based after
 * the first character has been consumed.
 */
public interface Buffer extends Comparable<Buffer> {

  /**
   * Return a substring from the underlying input.
   *
   * <p>The {@code stop} argument is exclusive, matching {@link String#substring(int, int)}. Match
   * callbacks use inclusive end offsets, so the usual way to recover callback text is {@code
   * buffer.getString(start, end + 1)}.
   *
   * @param start zero-based inclusive start offset
   * @param stop zero-based exclusive stop offset
   * @return text in the half-open range {@code [start, stop)}
   */
  String getString(final int start, final int stop);

  /**
   * Return whether a subsequent call to {@link #getNext()} can advance the cursor.
   *
   * @return {@code true} if another character is available
   */
  boolean hasNext();

  /**
   * Advance the cursor and return the next character.
   *
   * @return next character in the input
   */
  Character getNext();

  /**
   * Return the current zero-based cursor position.
   *
   * <p>Before any characters are consumed, implementations may return {@code -1}.
   *
   * @return current cursor position
   */
  int getCurrentPos(); // XXX Should this be a long?

  /**
   * Return an independent cursor over the same input content.
   *
   * <p>Partitioned matchers clone buffers before scanning in parallel. Implementations should make
   * sure advancing the clone does not advance the original.
   *
   * @return independent buffer clone
   */
  Buffer clone();

  private static int compoundCompare(int... results) {
    for (int result : results) {
      if (result != 0) {
        return result;
      }
    }
    return 0;
  }

  @Override
  default int compareTo(Buffer other) {
    return compoundCompare(
        Integer.compare(this.getCurrentPos(), other.getCurrentPos()),
        this.getString(0, this.getCurrentPos())
            .compareTo(other.getString(0, other.getCurrentPos())),
        Boolean.compare(this.hasNext(), other.hasNext()));
  }
}
