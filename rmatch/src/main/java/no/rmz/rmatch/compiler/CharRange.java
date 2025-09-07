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

/** A intermidate structure used by the compiler to represent ranges of characters. */
public final class CharRange implements Comparable<CharRange> {

  /** The smallest character in the range. */
  private final Character start;

  /** The largest character in the range. */
  private final Character end;

  /**
   * Create a new character range.
   *
   * @param start The smallest character in the range.
   * @param end The largest character in the range.
   */
  public CharRange(final Character start, final Character end) {
    this.start = start;
    this.end = end;
  }

  /**
   * The smallest character in the range.
   *
   * @return the smallest char.
   */
  public Character getStart() {
    return start;
  }

  /**
   * Get the largest character in the range.
   *
   * @return the largest char.
   */
  public Character getEnd() {
    return end;
  }

  @Override
  public int compareTo(final CharRange that) {

    int r = start.compareTo(that.start);
    if (r != 0) {
      return r;
    }

    return end.compareTo(that.end);
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof CharRange) {
      final CharRange that = (CharRange) o;
      return (this.start.equals(that.start) && this.end.equals(that.end));
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 37 * hash + (this.start != null ? this.start.hashCode() : 0);
    hash = 37 * hash + (this.end != null ? this.end.hashCode() : 0);
    return hash;
  }
}
