/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.rmz.rmatch.impls;

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import no.rmz.rmatch.interfaces.DFANode;
import no.rmz.rmatch.interfaces.MatchContext;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.NodeStorage;
import no.rmz.rmatch.utils.SortedSetComparatorImpl;

/**
 * Default {@link NodeStorage} implementation for lazy subset construction.
 *
 * <p>The storage owns one global {@link StartNode}. Every compiled expression is attached to that
 * start node by an epsilon edge, and DFA nodes are created on demand from sets of reachable NDFA
 * nodes as input is scanned.
 */
public final class NodeStorageImpl implements NodeStorage {

  /**
   * A comparator for ordered sets of NDFA Nodes that will deem a set larger than the other by
   * finding the first element (as defined by the ordering of the sets) that is different. A
   * comparison of the differing element determines the ordering of the sets.
   */
  private static final Comparator<SortedSet<NDFANode>> SORTED_NDFANODE_SET_COMPARATOR =
      new SortedSetComparatorImpl<>();

  /** There is only one start node, and this is that node. */
  private final StartNode sn;

  /** Map from NDFA state sets to the DFA nodes that represent them. */
  private final Map<SortedSet<NDFANode>, DFANode> ndfamap =
      new ConcurrentSkipListMap<>(SORTED_NDFANODE_SET_COMPARATOR);

  /**
   * A compressed map using CompressedDFAState as keys for faster lookups and reduced memory usage.
   * This is used in parallel with ndfamap for performance optimization.
   */
  private final ConcurrentMap<CompressedDFAState, DFANode> compressedNdfaMap =
      new ConcurrentHashMap<>();

  /** Create a new instance of the node storage. */
  public NodeStorageImpl() {
    sn = new StartNode(this);
  }

  @Override
  public void addToStartnode(final NDFANode n) {
    checkNotNull(n, "Illegal to add null NDFANode");
    sn.add(n);
    // A new regexp may start with characters that previously had no (or a different) start
    // transition, so the cached start transitions must be recomputed.
    Arrays.fill(asciiNextFromStart, null);
    nextFromDFAMap.clear();
  }

  /**
   * Return whether an NDFA node is attached directly to the global start node.
   *
   * <p>This method is primarily for tests and diagnostics.
   *
   * @param n NDFA node to check
   * @return {@code true} if {@code n} is an epsilon destination of the start node
   */
  public boolean isConnectedToStartnode(final NDFANode n) {
    checkNotNull(n, "Illegal to look for null NDFANode");
    return sn.getEpsilons().contains(n);
  }

  private final ConcurrentHashMap<Character, DFANode> nextFromDFAMap = new ConcurrentHashMap<>();

  /** Number of characters covered by the ASCII fast-path array. */
  private static final int ASCII_LIMIT = 128;

  /**
   * Marks an ASCII character known to start no regexp. Enables negative caching: the
   * ConcurrentHashMap path cannot store nulls, so it re-scans all NDFA epsilon edges on every
   * occurrence of such characters.
   */
  private static final DFANode NO_START = new DFANodeImpl(Collections.emptySet());

  /** Direct-indexed start-node transitions for ASCII characters. */
  private final DFANode[] asciiNextFromStart = new DFANode[ASCII_LIMIT];

  /** True when at least one compiled regexp uses zero-width assertions. */
  private volatile boolean contextAssertionsUsed = false;

  // XXX This is really startnode specific and shouldn't necessarily
  //     be tightly coupled with the NodeStorage implementation.
  @Override
  public DFANode getNextFromStartNode(final Character ch) {
    final char c = ch;
    if (c < ASCII_LIMIT) {
      final DFANode cached = asciiNextFromStart[c];
      if (cached != null) {
        return cached == NO_START ? null : cached;
      }
      final DFANode computed = sn.getNextDFA(ch, this);
      asciiNextFromStart[c] = computed == null ? NO_START : computed;
      return computed;
    }
    return nextFromDFAMap.computeIfAbsent(ch, key -> sn.getNextDFA(ch, this));
  }

  @Override
  public DFANode getNextFromStartNode(final Character ch, final MatchContext context) {
    if (context == MatchContext.NONE) {
      return getNextFromStartNode(ch);
    }
    return sn.getNextDFA(ch, this, context);
  }

  @Override
  public void markContextAssertionsUsed() {
    contextAssertionsUsed = true;
    Arrays.fill(asciiNextFromStart, null);
    nextFromDFAMap.clear();
  }

  @Override
  public boolean hasContextAssertions() {
    return contextAssertionsUsed;
  }

  @Override
  public DFANode getDFANode(final SortedSet<NDFANode> ndfaset) {
    checkNotNull(ndfaset, "Illegal to use  null Set of NDFANodes");

    // First try compressed lookup for better performance
    NDFANodeIdMapper.getInstance().registerNodes(ndfaset);
    CompressedDFAState compressedKey = new CompressedDFAState(ndfaset);
    DFANode result = compressedNdfaMap.get(compressedKey);

    if (result != null) {
      return result;
    }

    // Fall back to original lookup if not in compressed map
    result = ndfamap.get(ndfaset);

    if (result == null) {
      result = new DFANodeImpl(ndfaset);

      // Store in both maps for future lookups
      DFANode existingOriginal = ndfamap.putIfAbsent(ndfaset, result);
      DFANode existingCompressed = compressedNdfaMap.putIfAbsent(compressedKey, result);

      if (existingOriginal == null && existingCompressed == null) {
        // We successfully added our new node to both maps
        updateFinalStatuses(result, ndfaset);
      } else {
        // Someone else added it first, use their version and ensure both maps are consistent
        result = existingOriginal != null ? existingOriginal : existingCompressed;
        // Ensure the result is in both maps
        compressedNdfaMap.putIfAbsent(compressedKey, result);
        ndfamap.putIfAbsent(ndfaset, result);
      }
    } else {
      // Found in original map, add to compressed map for future performance
      compressedNdfaMap.putIfAbsent(compressedKey, result);
    }

    return result;
  }

  /**
   * Update expression terminal-node state for a newly created DFA node.
   *
   * @param dfaNode DFA node that represents {@code ndfaset}
   * @param ndfaset NDFA state set represented by {@code dfaNode}
   */
  private void updateFinalStatuses(final DFANode dfaNode, final Collection<NDFANode> ndfaset) {

    checkNotNull(ndfaset);
    checkNotNull(dfaNode);

    for (final NDFANode node : ndfaset) {
      if (node.isTerminal()) {
        node.getRegexp().addTerminalNode(dfaNode);
      }
    }
  }
}
