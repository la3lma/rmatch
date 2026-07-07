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
 * Callback interface used by the surface parser while reading a regular expression.
 *
 * <p>The parser recognizes syntax and reports semantic events here: literal characters, character
 * sets, alternation, quantifiers, groups, anchors, and word boundaries. Implementations decide how
 * those events become an executable representation. The production implementation builds NDFA
 * fragments.
 */
public interface AbstractRegexBuilder {

  /**
   * Add a string, to be treated as a sequence of individual characters.
   *
   * @param str a string of characters to add.
   */
  void addString(final String str);

  /**
   * When parsing a set of alternatives (e.g. "a|b|c"), this method is invoked between each of the
   * alternatives.
   */
  void separateAlternatives();

  /**
   * When parsing a character set such as {@code [abc]}, this method is invoked before the parser
   * reads the body of the set.
   */
  void startCharSet();

  /**
   * When parsing a character set, this method is invoked after the parser has read the full set.
   */
  void endCharSet();

  /**
   * This method is invoked immediately after {@link #startCharSet()} when the set is negated.
   *
   * <p>For example, {@code [^a]} matches every character except {@code a}.
   */
  void invertCharSet();

  /**
   * Add all characters in the supplied string to the current character set.
   *
   * @param cs characters to add to the current set
   */
  void addToCharSet(final String cs);

  /**
   * Add a closed character range to the current character set.
   *
   * <p>The resulting range matches every character greater than or equal to {@code startOfRange}
   * and less than or equal to {@code endOfRange}.
   *
   * @param startOfRange first character in the range
   * @param endOfRange last character in the range
   */
  void addRangeToCharSet(final char startOfRange, final char endOfRange);

  /** Add a pattern matching any character ({@code .}). */
  void addAnyChar();

  /** Add a zero-width assertion matching the beginning of a line ({@code ^}). */
  void addBeginningOfLine();

  /** Add a zero-width assertion matching the end of a line ({@code $}). */
  void addEndOfLine();

  /** Add a zero-width assertion matching an ASCII word boundary ({@code \b}). */
  void addWordBoundary();

  /** Add a zero-width assertion matching the absence of an ASCII word boundary ({@code \B}). */
  void addNonWordBoundary();

  /**
   * Add a pattern matching an optional, but singular element: E.g. "a?" denoting zero or one
   * instances of the character "a".
   */
  void addOptionalSingular();

  /** Add a pattern matching an element that should be repeated zero or many times, e.g. "a*". */
  void addOptionalZeroOrMulti();

  /** Add a pattern matching an element that should be repeated once or many times, e.g. "a+". */
  void addOptionalOnceOrMulti();

  /**
   * Start a group ("(" or "(?:"). Subsequent input builds the group's subexpression until endGroup
   * is called. Groups nest.
   */
  default void startGroup() {
    throw new UnsupportedOperationException("groups not supported by this builder");
  }

  /** End the innermost open group; the group becomes a single quantifiable atom. */
  default void endGroup() {
    throw new UnsupportedOperationException("groups not supported by this builder");
  }
}
