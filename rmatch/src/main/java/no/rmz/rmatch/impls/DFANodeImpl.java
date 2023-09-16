/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.rmz.rmatch.impls;

import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.utils.Counter;
import no.rmz.rmatch.utils.Counters;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An implementation of deterministic finite automata nodes DFA.
 */
public final class DFANodeImpl implements DFANode {

    /**
     * Counter used to figure out both how many DFA nodes are allocated, and to
     * generate unique IDs for the nodes (put in the "id" variable).
     */
    private static final Counter COUNTER = Counters.newCounter("DFANodeImpl");
    /**
     * A counter for known edges going to other DFAs.
     */
    private static final Counter KNOWN_DFA_EDGES_COUNTER =
            Counters.newCounter("Known DFA Edges");

    /**
     * The set of regular expression this node represents.
     */
    private final Set<Regexp> regexps = new HashSet<>();
    /**
     * A map of computed edges going out of this node. There may be more edges
     * going out of this node, but these are the nodes that has been encountered
     * so far during matching.
     */
    private final ConcurrentMap<Character, DFANode> nextMap = new ConcurrentHashMap<>();

    /**
     * The set of NDFANodes that this DFA node is representing.  It is immutable!
     * TODO: Make this into an immutable set.
     */
    private final SortedSet<NDFANode> basis = new TreeSet<>();

    /**
     * The set of regular expressions for which this node will make a match
     * fail.
     */
    private final Set<Regexp> isFailingSet = ConcurrentHashMap.newKeySet();

    /**
     * An unique (per VM) id for this DFANode.
     */
    private final long id;
    /**
     * A cache used to memoize check for finality for particular regexps.
     */
    private final Map<Regexp, Boolean> baseIsFinalCache = new ConcurrentHashMap<>();

    /**
     * Create a new DFA based representing a set of NDFA nodes.
     *
     * @param ndfanodeset the set of NDFA nodes the new DFA node should
     * represent.
     */
    @SuppressWarnings("SpellCheckingInspection")
    public DFANodeImpl(final Set<NDFANode> ndfanodeset) {
        basis.addAll(ndfanodeset);
        initialize(basis);
        id = COUNTER.inc();
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
    @Override
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
    public Set<Regexp> getRegexps() {
        return Collections.unmodifiableSet(regexps);
    }

    @Override
    public void addLink(final Character c, final DFANode n) {
        nextMap.put(c, n);
    }

    @Override
    public boolean hasLinkFor(final Character c) {
        return nextMap.containsKey(c);
    }

    /**
     * Get the next basis for a DFANode by pursuing the current basis through
     * the character.
     *
     * @param ch the character to explore.
     * @return A set of NDFANodes that serves as basis for the next DFANode.
     */
    private SortedSet<NDFANode> getNextThroughBasis(final Character ch) {
        final TreeSet<NDFANode> result = new TreeSet<>();

        for (final NDFANode n : basis) {
            result.addAll(n.getNextSet(ch));
        }

        return result;
    }

    @Override
    public DFANode getNext(final Character ch, final NodeStorage ns) {
        return nextMap.computeIfAbsent(ch, key -> {
            KNOWN_DFA_EDGES_COUNTER.inc();

            final SortedSet<NDFANode> nodes = getNextThroughBasis(ch);
            if (!nodes.isEmpty()) {
                return ns.getDFANode(nodes);
            }
            return null;
        });
    }

    @Override
    public void removeLink(final Character c) {
        nextMap.remove(c);
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
        return baseIsFinalCache.computeIfAbsent(r, key ->
                basis.stream().anyMatch(n -> key.hasTerminalNdfaNode(n))
        );
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
