/*
  Copyright 2012. Bjørn Remseth (rmz@rmz.no).

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package no.rmz.rmatch.compiler;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A parser for regular expressions, will parse strings, and generate abstract
 * regular expression trees from those strings through an AbstractRegexBuilder.
 */
public final class SurfaceRegexpParser {

    /**
     * Boolean constants are poor man's enumerations. ;)
     */
    private static final boolean COMMIT_ONLY_IF_SOMETHING_IN_SB = true;

    /**
     * Another poor man's enum.
     */
    private static final boolean COMMIT_EMPTY_STRING_IF_NOTHING_IN_SB = false;

    /**
     * A recipient of abstract regexp syntax, used as a backend for
     * the compiler.
     */
    private final AbstractRegexBuilder arb;

    /**
     * Create a new parser with a ARB backend.
     *
     * @param arb Compiler backend.
     */
    public SurfaceRegexpParser(final AbstractRegexBuilder arb) {
        this.arb = checkNotNull(arb);

    }

    /**
     * Parse a string int a regular expression.
     *
     * @param regexString the string to parse.
     * @throws RegexpParserException when bd things happen.
     */
    public void parse(final String regexString)
            throws RegexpParserException {
        new PAux(regexString, arb).parse();
    }

    /**
     * A helper class used to parse regular expressions.
     */
    static final class PAux {

        /**
         * A compiler backend.
         */
        private final AbstractRegexBuilder arb;

        /**
         * A StringBuilder used to parse string segments in the input
         * regexp.
         */
        private StringBuilder sb;

        /**
         * A source of characters based on the input string.
         */
        private final StringSource src;

        /**
         * Create a new helper class instance.
         *
         * @param regexString the string to parse.
         * @param arb         the builder to use.
         */
        PAux(final String regexString, final AbstractRegexBuilder arb) {
            this.arb = checkNotNull(arb);
            this.sb = new StringBuilder();
            this.src = new StringSource(regexString);
        }

        /**
         * send the current string to the compiler backend, then
         * start a new StringBuilder.
         *
         * @param ifNotEmpty XXX Don't understand this.
         */
        private void commitCurrentString(final boolean ifNotEmpty) {
            final String str = sb.toString();
            if (ifNotEmpty) {
                if (str.isEmpty()) {
                    return;
                }
            }
            arb.addString(str);
            sb = new StringBuilder();
        }

        /**
         * The objective is to parse all legal regexps as described in
         * http://en.wikipedia.org/wiki/Regular_expression That's an interesting
         * goal in itself, however it may in fact be better to emulate java's
         * regexp syntax.
         * <p>
         * That's the lofty objectives, the reality is much more humble.
         * We can parse this expression "abc[ab][^de]z?f+x*|y" and expressions
         * containing the same constructs (character sequences,
         * character sets (and inverted sets), various optional subexpressions
         * of single-char length, and not much more.   This will eventually
         * change, but for now that's what we've got.
         *
         * @throws RegexpParserException when bad things happen during parsing.
         */
        void parse() throws RegexpParserException {
            while (src.hasNext()) {
                final char ch = src.next();
                parseNextChar(ch);
            }
            commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
        }


        // XXX Missing {m,n}, meaning "match at least m,
        //     but no more than n times modifier.

        private void parseNextChar(char ch) throws RegexpParserException {
            switch (ch) {
                case '|':
                    commitCurrentString(COMMIT_EMPTY_STRING_IF_NOTHING_IN_SB);
                    arb.separateAlternatives();
                    break;
                case '\\':
                    parseQuotedChar();
                    break;
                case '.':
                    commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
                    arb.addAnyChar();
                    break;
                case '^':
                    commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
                    arb.addBeginningOfLine();
                    break;
                case '$':
                    commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
                    arb.addEndOfLine();
                    break;
                case '?':
                    commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
                    arb.addOptionalSingular();
                    break;
                case '*':
                    commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
                    arb.addOptionalZeroOrMulti();
                    break;
                case '+':
                    commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
                    arb.addOptionalOnceOrMulti();
                    break;
                case '[':
                    commitCurrentString(COMMIT_ONLY_IF_SOMETHING_IN_SB);
                    parseCharSet();
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }

        private void parseQuotedChar() throws RegexpParserException {
            char ch;
            if (src.hasNext()) {
                throw new RegexpParserException(
                        "Expected char after escape char: \\");
            }
            ch = src.next();
            sb.append(ch);
        }

        private void parseCharSet() throws RegexpParserException {
            char ch;
            arb.startCharSet();
            final Character nxt = src.peek();


            if (nxt == null) {
                throw new RegexpParserException(
                        "Unterminated char set, missing ']'");
            }

            if (nxt == '^') {
                arb.invertCharSet();
                src.next();
            }

            boolean parsingRange = false;
            while (src.hasNext()) {
                ch = src.next();
                if (ch == ']') {
                    break;
                } else if (ch == '-') {
                    parsingRange = true;
                } else if (parsingRange) {
                    final String s = sb.toString();
                    final int l = sb.length();
                    if (l > 1) {
                        arb.addToCharSet(s.substring(0, l - 1));
                    }
                    arb.addRangeToCharSet(s.charAt(l - 1), ch);
                    sb = new StringBuilder();
                    parsingRange = false;
                } else {
                    sb.append(ch);
                }
            }

            final String cs = sb.toString();
            if (!cs.isEmpty()) {
                arb.addToCharSet(cs);
                sb = new StringBuilder();
            }

            arb.endCharSet();
        }
    }
}
