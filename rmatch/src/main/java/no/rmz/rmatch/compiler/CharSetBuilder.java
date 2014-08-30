/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.compiler;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.PrintableEdge;
import no.rmz.rmatch.interfaces.Regexp;

/**
 * A builder for a CharSet.
 */
public final class CharSetBuilder {

    /**
     * The set of characters that this builder will look for.
     */
    private final StringBuilder charSetStringBuilder = new StringBuilder();

    /**
     * A collection of char-ranges that this matcher will look for.
     */
    private final Set<CharRange> charRanges = new TreeSet<CharRange>();

    /**
     * True iff this CharSet should be treated as an "inverted" CharSet,
     * i.e. one that will match everything -except- the characters in
     * the char set represented by the CharSet.
     */
    private boolean isInverted;

    /**
     *  The regexp that contains this CharSet.
     */
    private final Regexp regexp;

    /**
     * Create a new CharSet builder for a regexp.
     * @param regexp The regexp that the char set is a part of.
     */
    public CharSetBuilder(final Regexp regexp) {
        this.regexp = checkNotNull(regexp);
        isInverted = false;
    }

    /**
     * Build a CompiledFragment that represents the CharSet.
     * @return the compilation result.
     */
    public CompiledFragment build() {
        final CompiledFragment result = new CompiledFragment(regexp);
        final NDFANode arrival = result.getArrivalNode();
        final NDFANode endNode = result.getEndingNode();
        final String str = charSetStringBuilder.toString();
        final NDFANode intermediateNode;


        // If we're compiled an inverted set, then  create a graph that
        // will let any character pass but if we get one of the
        // characters of the character set, then we will fail the match
        // by getting to  a failing node.
        if (isInverted) {
            final NDFANode failingMatchNode = new FailNode(regexp);
            intermediateNode = failingMatchNode;
            final NDFANode gettingThroughAnyhow =
                    new AbstractNDFANode(regexp, false) {
                        @Override
                        public NDFANode getNextNDFA(final Character ch) {
                            return endNode;
                        }

                        @Override
                        public Collection<PrintableEdge> getEdgesToPrint() {
                            final Collection<PrintableEdge> result =
                                    getEpsilonEdgesToPrint();
                            result.add(new PrintableEdge(".", endNode));
                            return result;
                        }
                    };
            arrival.addEpsilonEdge(gettingThroughAnyhow);
        } else {
            intermediateNode = endNode;
        }

        // To match, make an NDFA node per character in the set
        // and pass through to the intermediate node if matching
        // one of the chars.
        for (int i = str.length() - 1; i >= 0; i--) {
            final Character myChar = str.charAt(i);
            final NDFANode node =
                    new CharNode(intermediateNode, myChar, regexp);
            arrival.addEpsilonEdge(node);
        }

        // Add more opportunities to match by  NDFA node per range in the set
        // and pass through to the intermediate node if matching
        // one of the ranges.
        for (final CharRange range : charRanges) {
            final NDFANode node =
                    new CharRangeNode(range, regexp, intermediateNode);
            arrival.addEpsilonEdge(node);
        }

        return result;
    }

    /**
     * Add a bunch of characters to the set we're matching.
     * @param cs characters in a string ;)
     */
    void addChars(final String cs) {
        charSetStringBuilder.append(cs);
    }

    /**
     * Treat this set of characters inverted.
     */
    void invert() {
        isInverted = true;
    }

    /**
     * Add a range of characters.
     * @param startOfRange The first character in the range.
     * @param endOfRange The last character in the range.
     */
    void addRange(final char startOfRange, final char endOfRange) {
        charRanges.add(new CharRange(startOfRange, endOfRange));
    }
}
