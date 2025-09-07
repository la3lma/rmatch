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
package no.rmz.rmatch.engine.prefilter;

import java.util.Objects;

/**
 * A hint about a literal substring that can be extracted from a regular expression pattern.
 *
 * <p>This class represents a literal substring found within a regex pattern that can be used for
 * prefiltering. The prefilter can scan for these literals using efficient string matching
 * algorithms like Aho-Corasick, and only trigger regex matching when the literal is found.
 */
public final class LiteralHint {
  /** Stable ID for the pattern this literal came from. */
  public final int patternId;

  /** The literal substring extracted from the regex (non-empty). */
  public final String literal;

  /** Start offset of the literal inside the regex pattern string. */
  public final int offsetInRegex;

  /** True if literal is anchored at start (pattern has ^ before it with no consuming tokens). */
  public final boolean anchoredPrefix;

  /** True if the regex is case-insensitive overall (Pattern.CASE_INSENSITIVE). */
  public final boolean caseInsensitive;

  /** If literal starts at match index+k, this is k (0 if it's the prefix). */
  public final int literalOffsetInMatch;

  /**
   * Creates a new literal hint.
   *
   * @param patternId stable ID for the pattern this literal came from
   * @param literal the literal substring extracted from the regex (must be non-empty)
   * @param offsetInRegex start offset of the literal inside the regex pattern string
   * @param anchoredPrefix true if literal is anchored at start
   * @param caseInsensitive true if the regex is case-insensitive
   * @param literalOffsetInMatch offset of literal within the match (0 if prefix)
   */
  public LiteralHint(
      final int patternId,
      final String literal,
      final int offsetInRegex,
      final boolean anchoredPrefix,
      final boolean caseInsensitive,
      final int literalOffsetInMatch) {
    this.patternId = patternId;
    this.literal = literal;
    this.offsetInRegex = offsetInRegex;
    this.anchoredPrefix = anchoredPrefix;
    this.caseInsensitive = caseInsensitive;
    this.literalOffsetInMatch = literalOffsetInMatch;
  }

  @Override
  public String toString() {
    return "LiteralHint{"
        + "patternId="
        + patternId
        + ", literal='"
        + literal
        + '\''
        + ", anchoredPrefix="
        + anchoredPrefix
        + ", ci="
        + caseInsensitive
        + ", litOffsetInMatch="
        + literalOffsetInMatch
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LiteralHint)) {
      return false;
    }
    final LiteralHint that = (LiteralHint) o;
    return patternId == that.patternId
        && anchoredPrefix == that.anchoredPrefix
        && caseInsensitive == that.caseInsensitive
        && literalOffsetInMatch == that.literalOffsetInMatch
        && Objects.equals(literal, that.literal);
  }

  @Override
  public int hashCode() {
    return Objects.hash(patternId, literal, anchoredPrefix, caseInsensitive, literalOffsetInMatch);
  }
}
