/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *      http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.compiler;

import no.rmz.rmatch.abstracts.AbstractNDFANode;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.PrintableEdge;
import no.rmz.rmatch.interfaces.Regexp;

import java.util.Collection;

/**
 * A node that has an outgoing edge for a single, specific character.
 */
public final class CharNode extends AbstractNDFANode {

    /**
     * The node we will go to after matching this character.
     */
    private final NDFANode nextNode;
    /**
     * The specific character we are looking for.
     */
    private final Character ch;

    /**
     * Create a new node that will match a specific character.
     *
     * @param nextNode The node we will go to after matching this character.
     * @param ch The specific character we are looking for.
     * @param r The regexp we are matching for.
     */
    public CharNode(
            final NDFANode nextNode,
            final Character ch,
            final Regexp r) {
        this(nextNode, ch, r, false);
    }

    /**
     * Create a new node that will match a specific character.
     *
     * @param nextNode The node we will go to after matching this character.
     * @param ch The specific character we are looking for.
     * @param r The regexp we are matching for.
     * @param isTerminal True iff this node is terminal
     */
    private CharNode(
            final NDFANode nextNode,
            final Character ch,
            final Regexp r,
            final boolean isTerminal) {
        super(r, isTerminal);
        this.nextNode = nextNode;
        this.ch = ch;
    }

    @Override
    public NDFANode getNextNDFA(final Character chr) {
        if (ch.equals(chr)) {
            return nextNode;
        } else {
            return null;
        }
    }

    @Override
    public Collection<PrintableEdge> getEdgesToPrint() {
        synchronized (monitor) {
            final Collection<PrintableEdge> result = getEpsilonEdgesToPrint();
            result.add(new PrintableEdge(String.valueOf(ch), nextNode));
            return result;
        }
    }

    @Override
    public boolean cannotStartWith(final Character ch) {
        return ch != this.ch;
    }
}
