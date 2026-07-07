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
 * A node in rmatch's nondeterministic finite automaton.
 *
 * <p>This is engine machinery, not the normal application API. It is public because the compiler,
 * matcher, and historical diagnostic tools share it. Most users should register patterns through
 * {@link Matcher#add(String, Action)} and never need to construct or traverse {@code NDFANode}
 * instances directly.
 */
public interface NDFANode extends Node, Comparable<NDFANode> {

  /**
   * Add an epsilon edge from this node to another node.
   *
   * <p>An epsilon edge consumes no input. During closure calculation the engine may move across
   * such an edge without reading a character.
   *
   * @param n destination node
   */
  void addEpsilonEdge(final NDFANode n);

  /**
   * Add an assertion edge from this node to another node.
   *
   * <p>Like an epsilon edge, an assertion edge consumes no input. Unlike an epsilon edge, it may be
   * followed only when its zero-width condition is satisfied at the current input position.
   *
   * @param assertion assertion that must hold for the edge to be followed
   * @param n destination node
   */
  void addAssertionEdge(final ZeroWidthAssertion assertion, final NDFANode n);

  /**
   * Remove an epsilon edge from this node to the supplied destination.
   *
   * @param n destination node to remove from the epsilon-reachable set
   */
  void removeEpsilonReachableNode(final NDFANode n);

  /**
   * Return the direct epsilon destinations of this node.
   *
   * @return nodes reachable from this node without consuming input
   */
  SortedSet<NDFANode> getEpsilons();

  /**
   * Return the direct assertion edges leaving this node.
   *
   * @return zero-width assertion edges from this node
   */
  Collection<AssertionEdge> getAssertionEdges();

  /**
   * Return the node reached by consuming the supplied character directly from this node.
   *
   * <p>rmatch's compiled NDFAs keep at most one direct outgoing edge per character from a single
   * node. When a pattern needs to branch after consuming the same character, the compiler creates
   * one direct character edge and places the branch behind that node using epsilon edges.
   *
   * @param ch input character to consume
   * @return directly reachable node, or {@code null} when no such edge exists
   */
  NDFANode getNextNDFA(final Character ch);

  /**
   * Return every node reachable after consuming the supplied character.
   *
   * <p>The result includes the direct character destination and the epsilon closure reachable from
   * that destination. In other words, this is the set of NDFA states the engine may occupy after it
   * reads {@code ch} from this node.
   *
   * @param ch input character to consume
   * @return reachable node set, including epsilon closure
   */
  SortedSet<NDFANode> getNextSet(final Character ch);

  /**
   * Context-aware variant of {@link #getNextSet(Character)} for zero-width assertions.
   *
   * @param ch input character to consume
   * @param context positional context for assertions adjacent to this transition
   * @return reachable node set, including context-sensitive assertion closure
   */
  SortedSet<NDFANode> getNextSet(final Character ch, final MatchContext context);

  /**
   * Return the compiled regular expression that owns this node.
   *
   * @return owning regular-expression state
   */
  Regexp getRegexp();

  @Override
  boolean isActiveFor(final Regexp rexp);

  /**
   * Return an identifier that is unique within the owning matcher.
   *
   * @return matcher-local node identifier
   */
  Long getId();

  /**
   * Return whether this node is a valid termination point for its owning expression.
   *
   * <p>A terminal node means a match candidate is legally complete. The engine may still suppress a
   * completed candidate if another overlapping match dominates it.
   *
   * @return {@code true} when this node is terminal
   */
  boolean isTerminal();

  // XXX isTerminalFor and isTerminal is obviously overlapping in
  //     functionality.  This must be cleaned up.  The most obvious
  //     fix is to remove the isTerminalFor method and replace it
  //     with something else.
  @Override
  boolean isTerminalFor(final Regexp rexp);

  /**
   * Return whether reaching this node abandons candidates for its owning expression.
   *
   * <p>A failing node is used to model constructs such as negated character classes. If execution
   * reaches such a node, the current candidate has learned enough to know it cannot succeed.
   *
   * @return {@code true} when this node fails candidates for its owning expression
   */
  boolean isFailing();
}
