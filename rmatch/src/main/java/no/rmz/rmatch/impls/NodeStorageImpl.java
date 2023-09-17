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

import no.rmz.rmatch.interfaces.DFANode;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.NodeStorage;
import no.rmz.rmatch.interfaces.PrintableEdge;
import no.rmz.rmatch.utils.SortedSetComparatorImpl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Implement the subset construction mechanism, but also keep track of a
 * "StartNode" NDFA node that has the particular property that it is always
 * present, and all NDFAs that are added to the storage can be reached from the
 * startnode via an epsilon edge.
 */
public final class NodeStorageImpl implements NodeStorage {

    /**
     * A comparator for ordered sets of NDFA Nodes that will deem a set larger
     * than the other by finding the first element (as defined by the ordering
     * of the sets) that is different. A comparison of the differing element
     * determines the ordering of the sets.
     */
    private static final Comparator<SortedSet<NDFANode>>
            SORTED_NDFANODE_SET_COMPARATOR =
                    new SortedSetComparatorImpl<>();
    /**
     * There is only one start node, and this is that node.
     */
    private final StartNode sn;
    /**
     * A map mapping sorted sets of NDFANodes into DFAnodes. Used to map sets of
     * NDFANodes to previously compiled DFAnodes representing that set of
     * NDFANodes.
     */
    private final Map<SortedSet<NDFANode>, DFANode> ndfamap =
                new TreeMap<>(SORTED_NDFANODE_SET_COMPARATOR);

    /**
     * Create a new instance of the node storage.
     */
    public NodeStorageImpl() {
        sn = new StartNode(this);
    }

    @Override
    public Collection<NDFANode> getNDFANodes() {
        synchronized (ndfamap) {
            final Set<NDFANode> result = new HashSet<>();
            final Set<NDFANode> unexplored = new ConcurrentSkipListSet<>();
            unexplored.add(sn);

            while (!unexplored.isEmpty()) {

                final NDFANode current = unexplored.iterator().next();
                unexplored.remove(current);
                if (!result.contains(current)) {
                    result.add(current);
                    final Set<NDFANode> connectedNodes =
                            new HashSet<>(current.getEpsilons());
                    for (final PrintableEdge edge : current.getEdgesToPrint()) {
                        connectedNodes.add(edge.getDestination());
                    }
                    connectedNodes.removeAll(result);
                    unexplored.addAll(connectedNodes);
                }
            }
            result.add(sn);
            return result;
        }
    }

    @Override
    public Collection<DFANode> getDFANodes() {
        synchronized (this.ndfamap) {
            final List<DFANode>  result = new ArrayList<>();
            result.addAll(this.ndfamap.values());
            result.add(sn.asDfaNode());
            return result;
        }
    }

    @Override
    public void addToStartnode(final NDFANode n) {
        checkNotNull(n, "Illegal to add null NDFANode");
        sn.add(n);
    }

    /**
     * Checks if the internal representation of the NodeStorage has cached an
     * DFA representation for the NDFA node n.
     * <p>
     * This method is not part of the NodeStorage interface, and is thus
     * intended to be used only for testing. If it is ever used for anything
     * else, then the NodeStorage interface should be expanded to include it.
     *
     * @param n an NDFA node that we wish to know if is cached or not.
     * @return true iff the NDFNode is connected from the startnode through an
     * epsilon edge.
     */
    public boolean isConnectedToStartnode(final NDFANode n) {
        checkNotNull(n, "Illegal to look for null NDFANode");
        return sn.getEpsilons().contains(n);
    }

    private final ConcurrentHashMap<Character, DFANode> nextFromDFAMap =
            new ConcurrentHashMap<>();

    // XXX This is really startnode specific and shouldn't necessarily
    //     be tightly coupled with the NodeStorage implementation.
    @Override
    public DFANode getNextFromStartNode(final Character ch) {
        checkNotNull(ch, "Illegal to use null char");
        return nextFromDFAMap.computeIfAbsent(ch, key -> {
            return sn.getNextDFA(ch, this);
        });
    }


    @Override
    public DFANode getDFANode(final SortedSet<NDFANode> ndfaset) {
        checkNotNull(ndfaset, "Illegal to use  null Set of NDFANodes");

        synchronized (ndfamap) {
            DFANode result = ndfamap.get(ndfaset);

            if (result == null) {
                result = new DFANodeImpl(ndfaset);
                ndfamap.put(ndfaset, result);

                updateFinalStatuses(result, ndfaset);
            }

            return result;
        }
    }

    /**
     * Traverse all the nodes in a collection of NDFANodes and update all the
     * regexps that are made final by this DFANode.
     *
     * @param dfaNode the set of DFA nodes to update.
     * @param ndfaset The set of NDFA nodes to update.
     */
    private void updateFinalStatuses(
            final DFANode dfaNode,
            final Collection<NDFANode> ndfaset) {

        checkNotNull(ndfaset);
        checkNotNull(dfaNode);

        for (final NDFANode node : ndfaset) {
            if (node.isTerminal()) {
                node.getRegexp().addTerminalNode(dfaNode);
            }
        }
    }
}
