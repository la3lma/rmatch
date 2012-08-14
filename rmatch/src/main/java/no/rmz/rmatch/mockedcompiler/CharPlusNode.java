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

package no.rmz.rmatch.mockedcompiler;

import static com.google.common.base.Preconditions.checkNotNull;
import java.util.Collection;
import no.rmz.rmatch.abstracts.AbstractNDFANode;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.PrintableEdge;
import no.rmz.rmatch.interfaces.Regexp;

/**
 * A node, used for testing, that will recognize a single or more instances of a
 * particular character.
 */
public final class CharPlusNode extends AbstractNDFANode {

    /**
     * The character that will be recognized.
     */
    private final Character myChar;

    /**
     * Create a new CharPlusNode instance.
     *
     * @param ch The character that will be recognized.
     * @param r The regular expression that this node is representing.
     * @param isTerminal True iff this node represents legal terminations of the
     * regexp r.
     */
    public CharPlusNode(
            final Character ch,
            final Regexp r,
            final boolean isTerminal) {
        super(r, isTerminal);
        this.myChar = checkNotNull(ch, "char can't be null");
    }

    @Override
    public NDFANode getNextNDFA(final Character ch) {
        if (ch.equals(myChar)) {
            return this;
        } else {
            return null;
        }
    }

    @Override
    public Collection<PrintableEdge> getEdgesToPrint() {
        synchronized (monitor) {
            final Collection<PrintableEdge> result = getEpsilonEdgesToPrint();
            result.add(new PrintableEdge("" + myChar + "", this));
            return result;
        }
    }
}
