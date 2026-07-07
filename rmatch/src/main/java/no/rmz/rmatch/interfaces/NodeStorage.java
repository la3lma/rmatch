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
   * Add a compiled NDFA start node to this storage's global start node.
   *
   * @param n pattern start node to add
   */
  void addToStartnode(final NDFANode n);

  /**
   * Return the deterministic start transition for a first input character.
   *
   * @param ch first input character at a candidate start position
   * @return relevant DFA node, or {@code null} if no expression can start with {@code ch}
   */
  DFANode getNextFromStartNode(final Character ch);

  /**
   * Context-aware start transition used when zero-width assertions are present.
   *
   * @param ch an input character
   * @param context positional context for assertions adjacent to this transition
   * @return relevant DFA node, or {@code null} if no expression can start in this context
   */
  DFANode getNextFromStartNode(final Character ch, final MatchContext context);

  /** Mark this storage as containing at least one zero-width assertion edge. */
  void markContextAssertionsUsed();

  /** Return true when this storage contains zero-width assertion edges. */
  boolean hasContextAssertions();

  /**
   * Return the DFA node representing a set of NDFA nodes.
   *
   * <p>The storage may return an existing DFA node if this NDFA set has already been represented.
   *
   * @param ndfaset nondeterministic node set to represent as one deterministic state
   * @return deterministic node for {@code ndfaset}
   */
  DFANode getDFANode(final SortedSet<NDFANode> ndfaset);

  /**
   * Return a snapshot of currently stored NDFA nodes.
   *
   * @return known NDFA nodes
   */
  Collection<NDFANode> getNDFANodes();

  /**
   * Return a snapshot of currently stored DFA nodes.
   *
   * @return known DFA nodes
   */
  Collection<DFANode> getDFANodes();
}
