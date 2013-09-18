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

package no.rmz.rmatch.impls;

import static com.google.common.base.Preconditions.checkNotNull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import no.rmz.rmatch.interfaces.DFANode;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.MatchSet;
import no.rmz.rmatch.interfaces.Match;
import no.rmz.rmatch.interfaces.NodeStorage;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.utils.Counter;
import no.rmz.rmatch.utils.Counters;

/**
 * An implementation of deterministic finite automata nodes DFA.
 */
public final class DFANodeImpl implements DFANode {

    /**
     * Counter used to figure out both how many DFA nodes are allocated, and to
     * generate unique IDs for the nodes (put in the "id" variable).
     */
    private static Counter DFA_NODE_COUNTER = Counters.newCounter("DFANodeImpl");


    /**
     * A counter for known edges going to other DFAs.
     */
    private static final Counter KNOWN_DFA_EDGES_COUNTER =
            Counters.newCounter("Known DFA Edges");


    /**
     * The set of regular expression this node represents.
     */
    private Set<Regexp> regexps = new HashSet<Regexp>();
    /**
     * A map of computed edges going out of this node. There may be more edges
     * going out of this node, but these are the nodes that has been encountered
     * so far during matching.
     */
    private Map<Character, DFAEdge> nextMap = new HashMap<Character, DFAEdge>();

    /**
     * The set of NDFANodes that this DFA node is representing.
     */
    private SortedSet<NDFANode> basis = new TreeSet<NDFANode>();
    /**
     * Monitor for synchronized access to methods.
     */
    private final Object monitor = new Object();
    /**
     * The set of regular expressions for which this node will make a match
     * fail.
     */
    private Set<Regexp> isFailingSet = new HashSet<Regexp>();


    /**
     * An unique (per VM) id for this DFANode.
     */
    private final long id;



    /**
     * Create a new DFA based representing a set of NDFA nodes.
     *
     * @param ndfanodeset the set of NDFA nodes the new DFA node should
     * represent.
     */
    public DFANodeImpl(final Set<NDFANode> ndfanodeset) {
        basis.addAll(ndfanodeset);
        initialize(basis);
        id = DFA_NODE_COUNTER.inc();
    }

    /**
     * Initialize the new node based on a set of NDFA nodes. Notify the regexp
     * about this DFA node being active for that expr.
     *
     * @param ndfanodeset the set of NDFA nodes this DFA node is based on.
     */
    private void initialize(final Set<NDFANode> ndfanodeset) {
        for (final NDFANode node : ndfanodeset) {
            checkNotNull(node, "no null nodes allowed!");
            final Regexp r = checkNotNull(node.getRegexp(),
                    "Current regexp can't be null");
            addRegexp(r);
            r.addActive(this);
            if (node.isTerminal()) {
                r.addTerminalNode(this);
            }


            if (node.isFailing()) {
                isFailingSet.add(r);
            }
        }
    }

    /**
     * Return an unique (within this VM) id for this DFANode.
     *
     * @return the id
     */
    public long getId() {
        return id;
    }

    /**
     * Get the nextmap. This is a "backdoor" into the implementation, and should
     * only be used by those who know what they are doing. It is emphatically
     * not intended to be used during matching. Currently it is only used to
     * print graphs, and that is done when the matcher isn't running. Be
     * careful!
     *
     * @return the map of nodes going out of this DFA node.
     */
    public Map<Character, DFANode> getNextMap() {

        final Map<Character, DFANode> result = new HashMap<Character, DFANode>();
        for (final Map.Entry<Character, DFAEdge> entry: nextMap.entrySet() ) {
            if (entry.getValue().getTarget() != null) {
                result.put(entry.getKey(), entry.getValue().getTarget());
            }
        }

        return result;
    }

    @Override
    public boolean isActiveFor(final Regexp r) {
        return r.isActiveFor(this);
    }

    @Override
    public boolean isTerminalFor(final Regexp r) {
        return r.hasTerminalNdfaNode(this);
    }

    @Override
    public Match newMatch(final MatchSet ms, final Regexp r) {
        return new MatchImpl(ms, r, baseIsFinalFor(r));
    }

    @Override
    public void addRegexp(final Regexp r) {
        regexps.add(r);
    }

    @Override
    public Set getRegexps() {
        return regexps;
    }

    private boolean isKnown(final Character ch) {
        synchronized (monitor) {
            return nextMap.containsKey(ch);
        }
    }

    @Override
    public void addLink(final Character ch, final DFANode n) {
        synchronized (monitor) {
            nextMap.put(ch,  DFAEdge.newEdge(n));
            KNOWN_DFA_EDGES_COUNTER.inc();
        }
    }

    @Override
    public boolean hasLinkFor(final Character c) {
        return nextMap.containsKey(c);
    }

    /**
     * Get the next basis for a DFANode by persuing the current basis through
     * the character.
     *
     * @param ch the character to explore.
     * @return A set of NDFANodes that serves as basis for the next DFANode.
     */
    private SortedSet<NDFANode> getNextThroughBasis(final Character ch) {
        synchronized (monitor) {
            final SortedSet<NDFANode> result = new TreeSet<NDFANode>();
            for (final NDFANode n : basis) {
                final SortedSet<NDFANode> nextSet = n.getNextSet(ch);
                result.addAll(nextSet);
            }
            return result;
        }
    }

    @Override
    public DFANode getNext(final Character ch, final NodeStorage ns) {
        synchronized (monitor) {
            if (!isKnown(ch)) {
                final SortedSet<NDFANode> nodes = getNextThroughBasis(ch);

                final DFANode dfaNode;
                if (!nodes.isEmpty()) {
                    dfaNode = ns.getDFANode(nodes);
                } else {
                    dfaNode = null;
                }

                addLink(ch, dfaNode);
            }

            // Now, either this will return a node, or a null, and in
            // any case that is what we know is the node
            // we'll get to by following
            // the ch.
            return nextMap.get(ch).getTarget();
        }
    }

    @Override
    public void removeLink(final Character c) {
        synchronized (monitor) {
            nextMap.remove(c);
        }
    }

    @Override
    public void removeNode() {
        // XXX Iterate over all linkers, calling "removeLinkTo"
        // XXX for the relevant characters.
        // XXX Abstract?
        throw new RuntimeException("Not implemented, perhaps not called?");
    }
    /**
     * A cache used to memoize check for finality for particular regexprs.
     */
    private final Map<Regexp, Boolean> baseisFinalCache =
            new HashMap();

    /**
     * Calculate if the present DFANode is final for a particular regular
     * expression.
     *
     * @param r a regexp
     * @return true iff this node is final for r.
     */
    private boolean baseIsFinalFor(final Regexp r) {
        synchronized (monitor) {

            if (baseisFinalCache.containsKey(r)) {
                return baseisFinalCache.get(r);
            }

            for (final NDFANode n : basis) {
                if (r.hasTerminalNdfaNode(n)) {
                    baseisFinalCache.put(r, true);
                    return true;
                }
            }
            baseisFinalCache.put(r, false);
            return false;
        }
    }

    @Override
    public boolean failsSomeRegexps() {
        return !isFailingSet.isEmpty();
    }

    @Override
    public boolean isFailingFor(final Regexp regexp) {
        return isFailingSet.contains(regexp);
    }
}
