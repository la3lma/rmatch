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

package no.rmz.rmatch.impls;

// XXX Don't mix: Either use some sort of NDFA with
//     a charmap (and epsilons), or use something else. Don't mix!!!

import no.rmz.rmatch.abstracts.AbstractNDFANode;
import no.rmz.rmatch.interfaces.*;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A startnode is a special kind of node that a Node Storage has only one of. It
 * is used to initiate new matches, so all the NDFAs for all the regular
 * expression associated with that NodeStorage instance has an epsilon edge
 * going out of the StartNode into it.
 * <p>
 * A StartNode only has epsilon edges going in and out of it, but apart from
 * that it's nothing special.
 */
public final class StartNode extends AbstractNDFANode {
    /**
     * The regexp that is nominally associated with the start node. It is an
     * empty expression that does not match anything.
     */
    private static final Regexp START_NO_REGEXP = new RegexpImpl(""); // XX??
    private final RegexpStorage regexpStorage;

    /**
     * The DFA that represents the StartNode instance.
     */
    private DFANodeImpl topDFA = null;
    /**
     * A monitor that is used to synchronize access to the StartNode instance.
     */
    private final Object topDfaMonitor = new Object();

    /**
     * Map of directly outgoing NDFANodes. XXX Never added to. Review and most
     * likely delete.
     */
    private final Map<Character, NDFANode> ndfaOutMap;

    /**
     * Create a enw StartNode instance.
     *
     * @param  ns the node storage this StartNode is associated with.
     */
    public StartNode(final NodeStorage ns, RegexpStorage rs) {
        super(START_NO_REGEXP, false);
        this.regexpStorage = checkNotNull(rs);
        this.ndfaOutMap = new HashMap<>();
    }


    // XXX Since we already have the NodeStorage, why do we need
    //     a parameter for it? This is almost certainly a bug. Fix.
    /**
     * Get the next DFA for a specific character.
     *
     * @param ch The charater
     * @param ns The NodeStorage to use.
     * @return a new DFA node.
     */
    public DFANode getNextDFA(final Character ch, final NodeStorage ns) {

        synchronized (topDfaMonitor) {
            if (topDFA == null) {
                topDFA = new DFANodeImpl(Collections.EMPTY_SET);
            } else if (topDFA.hasLinkFor(ch)) {
                return topDFA.getNext(ch, ns);
            }
        }

        final SortedSet<NDFANode> nextSet = getNextSet(ch);

        final DFANode result;
        if (!nextSet.isEmpty()) {
            result = ns.getDFANode(nextSet);
            topDFA.addLink(ch, result);
        } else {
            result = null;
        }

        return result;
    }


    @Override
    public Set<Character> knownStarterChars() {
        final Set<Character> result = new HashSet<>();
        synchronized (monitor) {
            for (NDFANode epsilon : getEpsilons()) {
                Set<Character> partialResult = epsilon.knownStarterChars();
                if (partialResult.isEmpty()) {
                    return partialResult;
                }
                result.addAll(partialResult);
            }
        }
        return result;
    }


    /**
     * Add a new NDFA Node to the startnode.
     *
     * @param n The node to add through an epsilon edge.
     */
    public void add(final NDFANode n) {
        checkNotNull(n, "Can't add null NDFA node");
        addEpsilonEdge(n);
    }

    @Override
    public NDFANode getNextNDFA(final Character ch) {
        return ndfaOutMap.get(ch);
    }

    @Override
    public Collection<PrintableEdge> getEdgesToPrint() {
        return getEpsilonEdgesToPrint();
    }

    public RegexpStorage getRegexpStorage() {
        return this.regexpStorage;
    }
}
