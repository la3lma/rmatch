/*
  Copyright 2012. Bjørn Remseth (rmz@rmz.no).
  <p>
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
  <p>
       http://www.apache.org/licenses/LICENSE-2.0
  <p>
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package no.rmz.rmatch.compiler;

/**
 * This interface is used for builders of abstract syntax trees for regular
 * expressions. Its intended use is to be injected into a parser for the surface
 * syntax of regular expressions, which then parses that surface syntax, and
 * delegates to an AbstractRegexBuilder to build a proper abstract regular
 * expression tree. That tree is in turn sent over to a compiler that will
 * produce a more or less runnable representation of it, e.g. an NDFA.
 */
public interface AbstractRegexBuilder {

    /**
     * Add a string, to be treated as a sequence of individual characters.
     *
     * @param str a string of characters to add.
     */
    void addString(final String str);

    /**
     * When parsing a set of alternatives (e.g. "a|b|c"), this method is invoked
     * between each of the alternatives.
     */
    void separateAlternatives();

    /**
     * When parsing a char set (e.g. "[abc]", this method is invoked before the
     * body of the char set.
     */
    void startCharSet();

    /**
     * When parsing a char set, this method is invoked to indicate the end of
     * the char set parsing.
     */
    void endCharSet();

    /**
     * This method is invoked immediately after the invokation of the
     * "startCharSet" method, if the char set is encoded as an "inverse" char
     * set, i.e. one matching all other characters than the ones encoded by the
     * content of the char set (e.g. "[^a]" would match anything except the
     * letter "a"
     */
    void invertCharSet();

    /**
     * Add all the characters in the string are added to the char set.
     *
     * @param cs a string representing characters in a char seg.
     */
    void addToCharSet(final String cs);

    /**
     * A range matching all characters greather than or equal to the
     * startOfRange char, and less than or equal to the endOfRange.
     *
     * @param startOfRange Lirst character in range.
     * @param endOfRange Last character in range.
     */
    void addRangeToCharSet(final char startOfRange, final char endOfRange);

    /**
     * Add a pattern matching any char (".").
     */
    void addAnyChar();

    /**
     * Add a pattern matching the beginning of a line ("^").
     */
    void addBeginningOfLine();

    /**
     * Add a pattern matching the end of a line ("$").
     */
    void addEndOfLine();

    /**
     * Add a pattern matching an optional, but singular element: E.g. "a?"
     * denoting zero or one instances of the character "a".
     */
    void addOptionalSingular();

    /**
     * Add a pattern matching an element that should be repeated zero or many
     * times, e.g. "a*".
     */
    void addOptionalZeroOrMulti();

    /**
     * Add a pattern matching an element that should be repeated once or many
     * times, e.g. "a+".
     */
    void addOptionalOnceOrMulti();
}
