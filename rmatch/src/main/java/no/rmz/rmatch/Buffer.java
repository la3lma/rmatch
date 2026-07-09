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
package no.rmz.rmatch;

/**
 * Randomly addressable input text consumed by rmatch engines.
 *
 * <p>A buffer is pure content. Implementations expose characters by zero-based position and
 * substrings by half-open range; they hold no iteration state. Engines keep their own cursors,
 * which is what lets a partitioned matcher scan one buffer instance from several threads.
 * Implementations must therefore tolerate concurrent readers. Immutable content, such as the
 * string-backed buffers returned by {@link RMatch#stringBuffer(String)}, satisfies this trivially.
 *
 * <p>Positions are {@code long} so that implementations larger than the {@code int} range remain
 * possible. The built-in string-backed buffers are naturally limited by {@link String} and reject
 * out-of-range arguments.
 *
 * <p>End of input is expressed positionally: {@link #hasCharAt(long)} returning {@code false} means
 * the input ends before {@code pos}. There is deliberately no total-length method, so
 * bounded-window implementations over long or streaming inputs stay expressible: such an
 * implementation may block in {@code hasCharAt} while fetching more input, and may restrict {@link
 * #getString(long, long)} to a documented retention window. Engines only scan forward and look back
 * at most one character for context assertions; match-text recovery via {@code getString} is the
 * caller's own lookback requirement.
 */
public interface Buffer {

  /**
   * Return whether the input contains a character at the given position.
   *
   * <p>Returning {@code false} means the input ends before {@code pos}. Implementations backed by a
   * stream may block here while fetching more input.
   *
   * @param pos zero-based position
   * @return {@code true} if {@link #charAt(long)} is defined for {@code pos}
   */
  boolean hasCharAt(long pos);

  /**
   * Return the character at the given position.
   *
   * <p>Defined when {@link #hasCharAt(long)} is {@code true} for {@code pos}.
   *
   * @param pos zero-based position
   * @return character at {@code pos}
   */
  char charAt(long pos);

  /**
   * Return the text in the half-open range {@code [start, stop)}.
   *
   * <p>The {@code stop} argument is exclusive, matching {@link String#substring(int, int)}. Match
   * callbacks use the same convention, so callback text is recovered with {@code
   * buffer.getString(start, end)}.
   *
   * <p>The default implementation assembles the result with {@link #charAt(long)}; string-backed
   * implementations should override it with a direct substring.
   *
   * @param start zero-based inclusive start offset
   * @param stop zero-based exclusive stop offset
   * @return text in the half-open range {@code [start, stop)}
   */
  default String getString(final long start, final long stop) {
    final StringBuilder sb = new StringBuilder(Math.toIntExact(stop - start));
    for (long i = start; i < stop; i++) {
      sb.append(charAt(i));
    }
    return sb.toString();
  }
}
