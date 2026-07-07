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
package no.rmz.rmatch.interfaces;

import java.util.Collection;
import java.util.SortedSet;

/**
 * Engine-internal storage for incremental subset construction.
 *
 * <p>When a pattern is compiled, rmatch first builds nondeterministic automaton nodes and then
 * creates deterministic nodes lazily as input is scanned. This keeps large pattern sets from
 * requiring all theoretical deterministic states up front. The public {@link
 * Matcher#getNodeStorage} hook exists for diagnostics and graph/debug tooling; application code
 * does not need to use this interface for normal matching.
 */
public interface NodeStorage {

  /**
   * Add a new NDFANode to the startnode associated with the NodeStoarge.
   *
   * @param n a node to add.
   */
  void addToStartnode(final NDFANode n);

  /**
   * Get the determinstic node that represents the beginning of all matches starting from the
   * startnode that begins with the character ch.
   *
   * @param ch an input character.
   * @return a relevant DFANode, or null if no node could be found.
   */
  DFANode getNextFromStartNode(final Character ch);

  /**
   * Context-aware start transition used when zero-width assertions are present.
   *
   * @param ch an input character
   * @param context positional context for assertions adjacent to this transition
   * @return a relevant DFANode, or null if no node could be found
   */
  DFANode getNextFromStartNode(final Character ch, final MatchContext context);

  /** Mark this storage as containing at least one zero-width assertion edge. */
  void markContextAssertionsUsed();

  /** Return true when this storage contains zero-width assertion edges. */
  boolean hasContextAssertions();

  /**
   * Given a set of NDFANodes, return a DFANode representing that set of NDFANOdes.
   *
   * @param ndfaset A set of nondeterminstic nodes we want to represent with a single deterministic
   *     node.
   * @return A new deterministic node representing the input.
   */
  DFANode getDFANode(final SortedSet<NDFANode> ndfaset);

  /**
   * Get a snapshot of the currently stored NDFANodes.
   *
   * @return All the NDFANodes know to the NodeStorage.
   */
  Collection<NDFANode> getNDFANodes();

  /**
   * Get a snapshot of the currently stored DFAodes.
   *
   * @return All the NDFANodes know to the NodeStorage.
   */
  Collection<DFANode> getDFANodes();
}
