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
import static java.lang.String.format;

import java.util.Collection;

import no.rmz.rmatch.abstracts.AbstractNDFANode;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.PrintableEdge;
import no.rmz.rmatch.interfaces.Regexp;

/**
 * A node representing a char range.
 */
public final class CharRangeNode extends AbstractNDFANode {

    /**
     * The first character in the range.
     */
    private final Character start;
    /**
     * The last character in the range.
     */
    private final Character end;
    /**
     * The node to return if the input is within the range.
     */
    private final NDFANode next;

    /**
     * Create a node representing a char range.
     *
     * @param start The first character in the range.
     * @param end The last character in the range.
     * @param r The regular expression associated with this node.
     * @param next The node to return if the input is within the range.
     */
    public CharRangeNode(
            final Character start,
            final Character end,
            final Regexp r,
            final NDFANode next) {
        super(r, false);
        this.start = checkNotNull(start);
        this.end = checkNotNull(end);
        this.next = checkNotNull(next);
        if (start.compareTo(end) > 0) {
            // XXX Use something else than runtime exception.
            throw new RuntimeException("Cannot have char range in which "
                    + " end is  less than start");
        }
    }

    /**
     * Create a new range node.
     *
     * @param range The range from which we get the start and end of the range.
     * @param r the regexp we're working for.
     * @param next The next NDFANode to go to if the input char is within the
     * range.
     */
    public CharRangeNode(
            final CharRange range,
            final Regexp r,
            final NDFANode next) {
        this(range.getStart(), range.getEnd(), r, next);
    }

    @Override
    public NDFANode getNextNDFA(final Character ch) {
        if (start.compareTo(ch) <= 0 && ch.compareTo(end) <= 0) {
            return next;
        } else {
            return null;
        }
    }

    @Override
    public Collection<PrintableEdge> getEdgesToPrint() {
        final PrintableEdge printableEdge =
                new PrintableEdge(
                format("%c-%c", start, end),
                next);
        final Collection<PrintableEdge> result = getEpsilonEdgesToPrint();
        result.add(printableEdge);
        return result;
    }
}
