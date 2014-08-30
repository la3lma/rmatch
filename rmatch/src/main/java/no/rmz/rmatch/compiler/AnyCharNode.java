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

import java.util.Collection;;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.PrintableEdge;
import no.rmz.rmatch.interfaces.Regexp;

/**
 * A node that matches any character.
 */
public final class AnyCharNode extends AbstractNDFANode {

    /**
     * The node to go to after matching the input character.
     */
    private final NDFANode nextNode;

    /**
     * Create a new "any" node matching any character.
     *
     * @param nextNode The character to go to after matching any character.
     * @param r The regexp that this node is a part of the representation for.
     */
    public AnyCharNode(final NDFANode nextNode, final Regexp r) {
        super(r, false);
        this.nextNode = checkNotNull(nextNode);
    }

    @Override
    public NDFANode getNextNDFA(final Character ch) {
        return nextNode;
    }

    @Override
    public Collection<PrintableEdge> getEdgesToPrint() {
        final Collection<PrintableEdge> result = getEpsilonEdgesToPrint();
        result.add(new PrintableEdge(".", nextNode));
        return result;
    }
}
