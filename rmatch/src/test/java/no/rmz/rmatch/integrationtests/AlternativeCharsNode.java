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

package no.rmz.rmatch.integrationtests;

import static com.google.common.base.Preconditions.checkNotNull;
import java.util.Collection;
import no.rmz.rmatch.compiler.AbstractNDFANode;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.PrintableEdge;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.compiler.CharNode;
import no.rmz.rmatch.compiler.TerminalNode;

/**
 * This is a convenience class that is intended only to be used for testing. It
 * tests fot the regexp "a|b" in a realistic way ("diamond" ndfa pattern), so if
 * this works, then the compiled code should work too.
 */
final class AlternativeCharsNode extends AbstractNDFANode {

    /**
     * One of the characters to allow through.
     */
    private final Character first;
    /**
     * The other character to allow through.
     */
    private final Character second;
    /**
     * The terminal node for the entire compiled regexp.
     */
    private final NDFANode terminal;

    /**
     * This is a convenience class that is intended only to be used for testing.
     * It tests fot the regexp "a|b" in a realistic way ("diamond" ndfa
     * pattern), so if this works, then the compiled code should work too.
     *
     * @param first One of the characters to allow through.
     * @param second The other character to allow through.
     * @param regexp The regexp we're compiling a representation for. (should
     * "first|second", or nothing is guaranteed to be consistent).
     */
    public AlternativeCharsNode(
            final Character first,
            final Character second,
            final Regexp regexp) {
        super(regexp, false);
        this.first = checkNotNull(first);
        this.second = checkNotNull(second);

        terminal = new TerminalNode(regexp);
        final NDFANode nf = new CharNode(terminal, first, regexp);
        final NDFANode ns = new CharNode(terminal, second, regexp);

        addEpsilonEdge(ns);
        addEpsilonEdge(nf);
    }

    /**
     * The root node doesn't know anything about any characters and will always
     * return null. It's the epsilon-connected children that does all the work.
     *
     * @param ch a character
     * @return null, always.
     */
    @Override
    public NDFANode getNextNDFA(final Character ch) {
        return null;
    }

    @Override
    public Collection<PrintableEdge> getEdgesToPrint() {
        final Collection<PrintableEdge> result = getEpsilonEdgesToPrint();
        result.add(new PrintableEdge("" + first, terminal));
        result.add(new PrintableEdge("" + second, terminal));
        return result;
    }
}
