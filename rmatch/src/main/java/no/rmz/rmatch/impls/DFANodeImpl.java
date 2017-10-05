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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import no.rmz.rmatch.interfaces.DFANode;
import no.rmz.rmatch.interfaces.Match;
import no.rmz.rmatch.interfaces.MatchSet;
import no.rmz.rmatch.interfaces.NDFANode;
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
    private static final Counter counter = Counters.newCounter("DFANodeImpl");
    /**
     * A counter for known edges going to other DFAs.
     */
    private static final Counter KNOWN_DFA_EDGES_COUNTER =
            Counters.newCounter("Known DFA Edges");

    /**
     * The set of regular expression this node represents.
     */
    private final Set<Regexp> regexps = new HashSet<Regexp>();
    /**
     * A map of computed edges going out of this node. There may be more edges
     * going out of this node, but these are the nodes that has been encountered
     * so far during matching.
     */
    private final Map<Character, DFANode> nextMap = new HashMap<>();
    /**
     * A map, corresponding to the nextMap, stating if the entry for a
     * particular character is valid or not.
     */
    private final Map<Character, Boolean> known = new HashMap<>();
    /**
     * The set of NDFANodes that this DFA node is representing.
     */
    private final SortedSet<NDFANode> basis = new TreeSet<>();
    /**
     * Monitor for synchronized access to methods.
     */
    private final Object monitor = new Object();
    /**
     * The set of regular expressions for which this node will make a match
     * fail.
     */
    private final Set<Regexp> isFailingSet = new HashSet<>();
    /**
     * An unique (per VM) id for this DFANode.
     */
    private final long id;
    /**
     * A cache used to memoize check for finality for particular regexprs.
     */
    private final Map<Regexp, Boolean> baseisFinalCache;

    /**
     * Create a new DFA based representing a set of NDFA nodes.
     *
     * @param ndfanodeset the set of NDFA nodes the new DFA node should
     * represent.
     */
    public DFANodeImpl(final Set<NDFANode> ndfanodeset) {
        this.baseisFinalCache = new HashMap<>();
        basis.addAll(ndfanodeset);
        initialize(basis);
        id = counter.inc();
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
        return Collections.unmodifiableMap(nextMap);
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
        return Collections.unmodifiableSet(regexps);
    }

    @Override
    public void addLink(final Character c, final DFANode n) {
        synchronized (monitor) {
            nextMap.put(c, n);
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
            final SortedSet<NDFANode> result = new TreeSet<>();
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
            if (known.containsKey(ch)) {
                return nextMap.get(ch);
            }

            final SortedSet<NDFANode> nodes = getNextThroughBasis(ch);

            if (!nodes.isEmpty()) {
                final DFANode dfaNode = ns.getDFANode(nodes);
                nextMap.put(ch, dfaNode);
            }

            KNOWN_DFA_EDGES_COUNTER.inc();
            known.put(ch, Boolean.TRUE);
        }

        // Now, either this will return a node, or a null, and in
        // any case that is what we know is the node
        // we'll get to by following
        // the ch.
        return nextMap.get(ch);
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
