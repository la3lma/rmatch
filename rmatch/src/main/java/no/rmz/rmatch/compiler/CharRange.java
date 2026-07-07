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

/**
 * Inclusive character range used while compiling character classes.
 *
 * @param start smallest character in the range
 * @param end largest character in the range
 */
public record CharRange(Character start, Character end) implements Comparable<CharRange> {

  /**
   * Create a new character range.
   *
   * @param start smallest character in the range
   * @param end largest character in the range
   */
  public CharRange {}

  /**
   * Return the smallest character in the range.
   *
   * @return range start
   */
  @Override
  public Character start() {
    return start;
  }

  /**
   * Return the largest character in the range.
   *
   * @return range end
   */
  @Override
  public Character end() {
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
    if (o instanceof CharRange that) {
      return (this.start.equals(that.start) && this.end.equals(that.end));
    } else {
      return false;
    }
  }
}
