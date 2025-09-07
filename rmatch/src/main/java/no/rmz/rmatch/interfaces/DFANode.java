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
 * Implement the Node interface for deterministic finite automatas (DFA)s. Implement the Node
 * interface for deterministic finite automatas (DFA)s.
 */
public interface DFANode extends Node {

  /**
   * Add a link going out through a character to a specific DFANOde.
   *
   * @param c the linking character
   * @param n the target node
   */
  void addLink(final Character c, final DFANode n);

  /**
   * Add a reference to a regep that is relevant for the present node.
   *
   * @param r a regexp.
   */
  void addRegexp(final Regexp r);

  /**
   * Get the DFAnode we can reach from here through a character.
   *
   * @param ch A character.
   * @param ns NodeStorage A node storage instance used to get new DFA nodes.
   * @return Return a determinstic node.
   */
  DFANode getNext(final Character ch, final NodeStorage ns);

  /**
   * Get the set of regexps that are associated with the present node.
   *
   * @return a set of regexps.
   */
  Set<Regexp> getRegexps();

  /**
   * Get the set of regexps that can start with the given character.
   * This optimizes the O(l*m) bottleneck by filtering regexps that cannot
   * possibly match starting with the given character.
   *
   * @param ch the character to filter by
   * @return a set of regexps that can start with the given character
   */
  Set<Regexp> getRegexpsThatCanStartWith(final Character ch);

  /**
   * True iff there is an outgoing link for the character.
   *
   * @param c the character
   * @return true iff there exists an outgoing link
   */
  boolean hasLinkFor(final Character c);

  @Override
  boolean isActiveFor(final Regexp r);

  @Override
  boolean isTerminalFor(final Regexp r);

  /**
   * Create a new match instance and add it to a MatchSet.
   *
   * @param ms A MatchSet.
   * @param r A regular expression.
   * @return A newly created Match.
   */
  Match newMatch(final MatchSet ms, final Regexp r);

  /**
   * Remove a link mapping the character c to somewhere.
   *
   * @param c a character
   */
  void removeLink(final Character c);

  /**
   * True if this DFA node will fail some regexp.
   *
   * @return true iff capable of failing a regexp.
   */
  boolean failsSomeRegexps();

  /**
   * True if this node fails for a particular regesp.
   *
   * @param regexp the regex we may be failing for.
   * @return true iff failing for regexp.
   */
  boolean isFailingFor(final Regexp regexp);

  /**
   * Return an unique long that identifies this DFA in this matcher engine. Mostly intended to be
   * used during debugging, to visualize the graph of the DFA.
   */
  long getId();
}
