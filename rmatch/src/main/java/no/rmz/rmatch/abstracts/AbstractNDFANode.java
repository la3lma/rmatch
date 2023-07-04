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

package no.rmz.rmatch.abstracts;

import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.PrintableEdge;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.utils.Counter;
import no.rmz.rmatch.utils.Counters;
import no.rmz.rmatch.utils.LifoSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An abstract implementation of the NDANode interface. In fact, this
 * implementation is designed so that only the getNextNDFA and getEdgesToPrint
 * methods should be implemented by subclasses.
 */
public abstract class AbstractNDFANode implements NDFANode {
    /**
     * Count the number of abstract ndfanodes we crate. This counter is used for
     * many things, not just for instrumentation. It is also used to generate
     * graphviz graphs and uniquely identify NDFAs in various contexts.
     */
    static final Counter COUNTER = Counters.newCounter("AbstractNDFANodes");
    /**
     * A counter that keeps track of how many edges that has been observed out
     * of this NDFA.
     */
    private static final Counter cachedEdgesCounter =
 Counters.newCounter("Cached edges going to an NDFA.");


    /**
     * The set of epsilon-edges going out of this node.
     */
    private final SortedSet<NDFANode> epsilonSet = new TreeSet<>();
    /**
     * The regular expression associated with this NDFA.
     */
    private final Regexp regexp;
    /**
     * True iff this ndfa is terminal for the regexp.
     */
    private final boolean isTerminal;
    /**
     * True iff reaching this node means that a match MUST fail.
     */
    private final boolean isFailing;
    /**
     * Monitor used to synchronize access to single methods.
     */
    protected final Object monitor = new Object();
    /**
     * An unique index for the Abstract DNFANodes. Generated by a counter.
     */
    private final Long index;
    /**
     * A cache that is used by the getNextSet method to memoize its results and
     * thus to achieve much quicker execution times.
     */
    private final Map<Character, SortedSet<NDFANode>> cachedNext =
 new HashMap<>();

    /**
     * Create a new AbstractNDFANode instance.
     *
     * @param r the regular expression associated with this node.
     * @param isTerminal Is this node terminal for the regexp?
     * @param isFailing true iff visiting this node means that a match will
     * fail!
     */
    public AbstractNDFANode(
            final Regexp r,
            final boolean isTerminal,
            final boolean isFailing) {
        this.regexp = checkNotNull(r, "Regexp can't be null for an NDFA node");
        this.isTerminal = isTerminal;
        this.isFailing = isFailing;

        index = COUNTER.inc();

        if (isTerminal) {
            r.addTerminalNode(this);
        }
    }

    /**
     * Create a new AbstractNDFANode instance.
     *
     * @param r the regular expression associated with this node.
     * @param isTerminal Is this node terminal for the regexp?
     */
    public AbstractNDFANode(
            final Regexp r,
            final boolean isTerminal) {
        this(r, isTerminal, false);
    }

    /**
     * Return an id that is unique for this NDFA instance.
     *
     * @return a long identifying the node.
     */
    public final Long getId() {
        // This doesn't need to be locked, since it is immutable.
        return index;
    }

    @Override
    public final boolean isTerminal() {
        return isTerminal;
    }

    @Override
    public final Regexp getRegexp() {
        return regexp;
    }

    @Override
    public final boolean isActiveFor(final Regexp rexp) {
        return rexp.isActiveFor(this);
    }

    @Override
    public final boolean isTerminalFor(final Regexp rexp) {
        return rexp.hasTerminalNdfaNode(this);
    }

    @Override
    public final boolean isFailing() {
        return isFailing;
    }

    /**
     * Implement breadth first search through the set of nodes for NDFA nodes
     * that are valid successors to the current node, through the parameter
     * "ch".
     *
     * @param ch the char we're finding the set of NDFAs for.
     * @return a set of NDFA nodes representing the next state for the DFA.
     */
    @Override
    public final SortedSet<NDFANode> getNextSet(final Character ch) {

        synchronized (monitor) {
            return getNextSetNonThreadsafe(ch);
        }
    }

    /**
     * Implement breadth first search through the set of nodes for NDFA nodes
     * that are valid successors to the current node, through the parameter
     * "ch".
     *
     * @param ch the char we're finding the set of NDFAs for.
     * @return a set of NDFA nodes representing the next state for the DFA.
     */
    private SortedSet<NDFANode> getNextSetNonThreadsafe(final Character ch) {
        // If we get a cache hit, return the cached entry
        if (cachedNext.containsKey(ch)) {
            return cachedNext.get(ch);
        }

        // Eventually the result we'll collect and return will go into this
        // set.
        final SortedSet<NDFANode> resultNodes = new TreeSet<>();

        // Meanwhile we'll have a set of unexplored nodes that we'll have to
        // explore before we're done.
        final LifoSet<NDFANode> unexploredNodes = new LifoSet<>();

        // The set of unexplored nodes start with the current node.
        unexploredNodes.add(this);

        final Set<NDFANode> visitedNodes = new HashSet<>();

        while (!unexploredNodes.isEmpty()) {

            // Get the first NDFA node
            final NDFANode current = unexploredNodes.pop();
            visitedNodes.add(current);

            // By pursuing the current NDFA node through both
            // character-specific and epsilon edges, we get a new
            // a new NDFA node that is reachble through this character.
            // If we  haven't expored this node, so we add it to the
            // set of unexplored nodes.
            final NDFANode nextNode = current.getNextNDFA(ch);

            if (nextNode != null) {
                resultNodes.add(nextNode);
            }

            final Set<NDFANode> newNodes
                    = extendByFollowingEpsilons(current);

            // Remove new nodes that are already in the resultNodes
            removeDuplicates(newNodes, resultNodes);

            if (!newNodes.isEmpty()) {
                newNodes.removeAll(visitedNodes);
                unexploredNodes.addAll(newNodes);
            }
        }

        followEpsilonLinks(resultNodes);

        // Updating the counter and the cache.
        cachedEdgesCounter.inc();
        cachedNext.put(ch, resultNodes);

        // The set of NDFANode instances
        // representing the next DFA node.
        return resultNodes;
    }

    private static void removeDuplicates(
            final Set<NDFANode> newNodes,
            final SortedSet<NDFANode> resultNodes) {
        if (!newNodes.isEmpty() && !resultNodes.isEmpty()) {
            newNodes.removeAll(resultNodes);
        }
    }

    private Set<NDFANode> extendByFollowingEpsilons(final NDFANode current) {
        // Now we calculate a set difference between the  nodes
        // that can be reached from the current node through its
        // epsilons, and all the nodes we have already put into the
        // result set.   The difference is added to the
        // set of unexplored nodes.
        final Set<NDFANode> newNodes = new HashSet<>();
        final Set<NDFANode> epsilons = current.getEpsilons();
        if (!epsilons.isEmpty()) {
            newNodes.addAll(epsilons);
        }
        return newNodes;
    }

    /**
     * Then follow all epsilon links for the nodes in the resultNodes set
     * (transitively reflexive closure of epsilon links) and add all of those to
     * the result set.
     */
    private static void followEpsilonLinks(final SortedSet<NDFANode> resultNodes) {

        final Set<NDFANode> epsilonClosure = new HashSet<>();
        for (final NDFANode r : resultNodes) {
            epsilonClosure.addAll(r.getEpsilons());
        }

        while (!epsilonClosure.isEmpty()) {
            final NDFANode next = epsilonClosure.iterator().next();
            if (resultNodes.add(next)) {
                epsilonClosure.addAll(next.getEpsilons());
            }
            epsilonClosure.remove(next);
        }
    }

    @Override
    public final SortedSet<NDFANode> getEpsilons() {
        synchronized (monitor) {
            return Collections.unmodifiableSortedSet(epsilonSet);
        }
    }

    @Override
    public final void addEpsilonEdge(final NDFANode n) {
        synchronized (monitor) {
            checkNotNull(n,
                    "It is meaningless to add a NULL NDFANode "
                    + "as an epsilon reachable node");
            epsilonSet.add(n);
        }
    }

    @Override
    public final void removeEpsilonReachableNode(final NDFANode n) {
        synchronized (monitor) {
            epsilonSet.remove(n);
        }
    }

    @Override
    public final int compareTo(@NotNull final NDFANode t) {
        if (!(t instanceof AbstractNDFANode ta)) {
            throw new UnsupportedOperationException("Not supported yet.");
        } else {

            return index.compareTo(ta.index);
        }
    }

    /**
     * Add a bunch of nodes that can be epsilon-reached.
     *
     * @param alternatives A set of epsilon-reachable nodes.
     */
    public final void addEpsilonReachableNodes(
            final Collection<NDFANode> alternatives) {
        synchronized (monitor) {
            for (final NDFANode n : alternatives) {
                addEpsilonEdge(n);
            }
        }
    }

    /**
     * This method is intended to be overridden. The subclass should first call
     * its superclass's getEdgesToPring method, to get all the epsilon edges,
     * and anything else the superclass chooses to include, and then add
     * whatever edges that the getNextNDFA method can link to.
     *
     * @return A collection of PrintableEdge instances.
     */
    public final Collection<PrintableEdge> getEpsilonEdgesToPrint() {
        final Collection<PrintableEdge> result = new ArrayList<>();
        synchronized (monitor) {
            for (final NDFANode n : epsilonSet) {
                result.add(new PrintableEdge(null, n));
            }
        }
        return result;
    }
}
