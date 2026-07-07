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

import java.util.Set;

/**
 * A node in rmatch's lazily constructed deterministic finite automaton.
 *
 * <p>This is engine machinery. The matcher builds DFA nodes from sets of {@link NDFANode}s as input
 * is scanned, instead of eagerly constructing every theoretical DFA state up front.
 */
public interface DFANode extends Node {

  /**
   * Add a deterministic transition for one input character.
   *
   * @param c input character that triggers the transition
   * @param n destination DFA node
   */
  void addLink(final Character c, final DFANode n);

  /**
   * Associate a compiled regular expression with this DFA node.
   *
   * @param r regular-expression state relevant to this node
   */
  void addRegexp(final Regexp r);

  /**
   * Return the deterministic node reached by consuming the supplied character.
   *
   * @param ch input character to consume
   * @param ns node storage used to create or reuse lazily constructed DFA nodes
   * @return reachable deterministic node, or {@code null} if no transition exists
   */
  DFANode getNext(final Character ch, final NodeStorage ns);

  /**
   * Context-aware transition used when the pattern set contains zero-width assertions.
   *
   * @param ch input character
   * @param ns node storage for subset construction
   * @param context positional context for assertions adjacent to this transition
   * @return reachable deterministic node, or {@code null} if no transition exists
   */
  DFANode getNext(final Character ch, final NodeStorage ns, final MatchContext context);

  /**
   * Return the regular expressions associated with this DFA node.
   *
   * @return associated regular-expression states
   */
  Set<Regexp> getRegexps();

  /**
   * Return the expressions that can begin by consuming the supplied character.
   *
   * <p>This first-character filter keeps the engine from starting candidates for expressions that
   * cannot possibly match at the current input position.
   *
   * @param ch the character to filter by
   * @return regular-expression states that can start with {@code ch}
   */
  Set<Regexp> getRegexpsThatCanStartWith(final Character ch);

  /**
   * Context-aware first-character filter used when assertions are active.
   *
   * @param ch the character to filter by
   * @param context positional context for assertions adjacent to this transition
   * @return regular-expression states that can start with the character in this context
   */
  Set<Regexp> getRegexpsThatCanStartWith(final Character ch, final MatchContext context);

  /**
   * Return whether this node has an outgoing transition for the supplied character.
   *
   * @param c input character
   * @return {@code true} if a transition for {@code c} exists
   */
  boolean hasLinkFor(final Character c);

  @Override
  boolean isActiveFor(final Regexp r);

  @Override
  boolean isTerminalFor(final Regexp r);

  /**
   * Create a match candidate for a regular expression starting from a match set.
   *
   * @param ms match set that will own the candidate
   * @param r regular-expression state being matched
   * @return newly created match candidate
   */
  Match newMatch(final MatchSet ms, final Regexp r);

  /**
   * Remove the transition for a character.
   *
   * @param c input character whose transition should be removed
   */
  void removeLink(final Character c);

  /**
   * Return whether this node fails at least one regular expression.
   *
   * @return {@code true} if this node can abandon candidates
   */
  boolean failsSomeRegexps();

  /**
   * Return whether this node fails candidates for a particular expression.
   *
   * @param regexp regular-expression state to check
   * @return {@code true} if this node fails candidates for {@code regexp}
   */
  boolean isFailingFor(final Regexp regexp);

  /**
   * Return an identifier that is unique within the owning matcher.
   *
   * @return matcher-local DFA node identifier
   */
  long getId();
}
