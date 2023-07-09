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
import java.util.HashSet;
import java.util.Set;

/**
 * This is a node that has only epsilon edges going into it and only epsilon
 * edges going out of it. It is used to pad other automata.
 */
public final class PaddingNDFANode extends AbstractNDFANode {

    private Collection<NDFANode> tfcoee;

    /**
     * Create a new padding node for a regular expression.
     *
     * @param r Our regexp.
     */
    public PaddingNDFANode(final Regexp r) {
        super(r, false);
    }

    @Override
    public NDFANode getNextNDFA(final Character ch) {
        return null;
    }

    @Override
    public Collection<PrintableEdge> getEdgesToPrint() {
        return getEpsilonEdgesToPrint();
    }

    @Override
    public boolean cannotStartWith(Character ch) {

        for (NDFANode n :  this.transitiveReflexiveClosureOfNonPaddingEpsilonEdges()) {
            if (n == this) {
                continue; // TODO:   A kludge!
            }
            if (n.mayBeAbleToStartWith(ch)) {
                return false;
            }
        }
        return true;
    }

    private final Object lock = new Object();
    private Collection<NDFANode> transitiveReflexiveClosureOfNonPaddingEpsilonEdges() {
        synchronized(lock) {
            if (this.tfcoee == null) {

                // First get the TR closure of epsilon edges
                this.tfcoee = new HashSet<>();

                // (Ignore this, since it's a padding node, but do get the
                // connected nodes.)
                final Set<NDFANode> edge = new HashSet<>(this.getEpsilons());
                while (!edge.isEmpty()) {
                    final Set<NDFANode> openedEdges = new HashSet<>();
                    for (NDFANode n : edge) {
                        if (!this.tfcoee.contains(n)) {
                            this.tfcoee.add(n);
                            openedEdges.addAll(n.getEpsilons());
                        }
                    }
                    edge.addAll(openedEdges);
                    edge.removeAll(this.tfcoee);
                }

                // Then remove the padding nodes
                this.tfcoee = this.tfcoee.stream().filter(n -> !(n instanceof PaddingNDFANode)).collect(java.util.stream.Collectors.toSet());
            }
            return this.tfcoee;
        }
    }
}
