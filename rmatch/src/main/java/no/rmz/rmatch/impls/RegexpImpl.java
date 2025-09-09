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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import no.rmz.rmatch.interfaces.*;

/** Representation of a parsed regular expression. */
public final class RegexpImpl implements Regexp {

  /** The Regexp string represented by this Regexp. */
  private final String rexpString;

  /**
   * The set of actions associated with this regular expression. These actions will be invoked when
   * a match for the regular expression is detected.
   */
  private final Set<Action> actions = new HashSet<>();

  /**
   * The set of nodes that are currently involved in matching expressions for this regular
   * expression.
   */
  private final Set<Node> activeNodes = new HashSet<>();

  /** The set of nodes that permit successful termination of matches for this regular expression. */
  private final Set<Node> terminatingNodes = new HashSet<>();

  /**
   * A map from MatchSets to Domination heaps, so that we can get a domination heap that is used to
   * represent the matches within a single match set.
   *
   * <p>XXX It is a bit unclear if it is right to put this map in the Regexp implementation. It may
   * in fact be much saner to put it into the MatchSet, however, the MatchSet doesn't use it itself.
   */
  private final ConcurrentHashMap<MatchSet, DominationHeap> heaps;

  /** The starting node in the NDFA that represents this regular expression. */
  private NDFANode myNode;

  /** Cache for first-character matching results to optimize O(l*m) bottleneck. */
  private final Map<Character, Boolean> firstCharacterCache = new HashMap<>();

  /**
   * Make a new instance of Regexp representing a regular expression.
   *
   * @param rexpString a string representation of the regular expression.
   */
  public RegexpImpl(final String rexpString) {
    this.heaps = new ConcurrentHashMap<>();

    checkNotNull(rexpString, "regexpString can't be null");
    this.rexpString = rexpString;
  }

  @Override
  public boolean isCompiled() {
    return (myNode != null);
  }

  @Override
  public void setMyNDFANode(final NDFANode myNode) {
    assert (!isCompiled());
    this.myNode = myNode;
  }

  @Override
  public NDFANode getMyNode() {
    return myNode;
  }

  @Override
  public boolean isActiveFor(final Node n) {
    checkNotNull(n);
    return activeNodes.contains(n);
  }

  @Override
  public void addActive(final Node n) {
    checkNotNull(n);
    activeNodes.add(n);
  }

  @Override
  public void addTerminalNode(final Node n) {
    checkNotNull(n);
    terminatingNodes.add(n);
  }

  @Override
  public boolean hasTerminalNdfaNode(final Node n) {
    checkNotNull(n);
    return terminatingNodes.contains(n);
  }

  @Override
  public String getRexpString() {
    return rexpString;
  }

  @Override
  public void add(final Action a) {
    checkNotNull(a);
    actions.add(a);
  }

  @Override
  public void remove(final Action a) {
    checkNotNull(a);
    actions.remove(a);
  }

  @Override
  public boolean hasAction(final Action a) {
    checkNotNull(a);
    return actions.contains(a);
  }

  @Override
  public boolean hasActions() {
    return !actions.isEmpty();
  }

  @Override
  public void performActions(final Buffer b, final int start, final int end) {
    for (final Action a : actions) {
      a.performMatch(b, start, end);
    }
  }

  @Override
  public DominationHeap getDominationHeapCreateIfNotPresent(final MatchSet ms) {
    return heaps.computeIfAbsent(ms, k -> new DominationHeap());
  }

  @Override
  public void abandonMatchSet(final MatchSet ms) {
    heaps.remove(ms);
  }

  @Override
  public void registerMatch(final Match m) {
    assert (m != null);
    getDominationHeapCreateIfNotPresent(m.getMatchSet()).addMatch(m);
    assert hasMatch(m);
  }

  /**
   * True iff m can never become dominating.
   *
   * @param m the node that may or may not dominate this strongy.
   * @return true iff this node is strongly dominated by m
   */
  @Override
  public boolean isStronglyDominated(final Match m) {
    return false; // XXX Possible future optimization
  }

  //  XXX Is "this" dominating argument?
  @Override
  public boolean isDominating(final Match m) {
    if (m.getRegexp() != this) {
      return false; // Can only dominate if regexp is the same
    }
    final DominationHeap dm = getDominationHeapCreateIfNotPresent(m.getMatchSet());
    if (dm.isEmpty()) {
      // If we don't know anyting about the ms of the m, we can't dominate
      return false;
    }
    final Match mostDominant = dm.getFirstMatch();
    if (m == mostDominant) {
      // The most dominant object  dominates itself? Weird but correct?
      return true;
    }
    return (Match.COMPARE_BY_DOMINATION.compare(mostDominant, m) > 0);
  }

  @Override
  public void abandonMatch(final Match m, final Character currentChar) {
    // TODO: All of those checks do take a significant amount of time.
    checkNotNull(m);
    checkArgument(this == m.getRegexp());
    checkArgument(hasMatches());
    checkArgument(hasMatch(m));

    final MatchSet ms = m.getMatchSet();

    final DominationHeap dh = heaps.get(ms);
    if (dh == null) {
      return;
    }

    dh.remove(m);
    if (dh.isEmpty()) {
      heaps.remove(ms);
    }
  }

  @Override
  public void commitUndominated(final RunnableMatchesHolder runnableMatches) {
    if (heaps.isEmpty()) {
      return;
    }

    // From each DominationHeap, only pick the most dominant (hence
    // un-dominated) element.
    for (final DominationHeap dh : heaps.values()) {
      // Commit the current match if it's final
      final Match firstMatch = dh.getFirstMatch();
      if (firstMatch.isFinal()) {
        runnableMatches.add(firstMatch);
      }
    }
    // Then clear the heaps
    heaps.clear();
  }

  @Override
  public String toString() {
    return "RegexpImpl{string = '" + rexpString + "', actions = " + actions + "}";
  }

  @Override
  public int compareTo(final Regexp other) {
    checkNotNull(other);
    return this.getRexpString().compareTo(other.getRexpString());
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof RegexpImpl) {
      return compareTo((Regexp) o) == 0;
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return this.rexpString.hashCode();
  }

  @Override
  public boolean hasMatch(final Match m) {
    checkNotNull(m);
    final MatchSet ms = m.getMatchSet();
    return (this.heaps != null) && this.heaps.containsKey(ms);
  }

  @Override
  public boolean hasMatches() {
    return (heaps != null) && (!heaps.isEmpty());
  }

  @Override
  public synchronized boolean canStartWith(final Character ch) {
    checkNotNull(ch, "Character cannot be null");

    // Return cached result if available
    Boolean cachedResult = firstCharacterCache.get(ch);
    if (cachedResult != null) {
      return cachedResult;
    }

    // Compute and cache the result
    boolean result = computeCanStartWith(ch);
    firstCharacterCache.put(ch, result);
    return result;
  }

  /**
   * Compute whether this regexp can start with the given character by examining the NDFA starting
   * from the root node, considering epsilon transitions.
   *
   * @param ch the character to check
   * @return true if the regexp can start with the given character
   */
  private boolean computeCanStartWith(final Character ch) {
    if (myNode == null) {
      return false; // Not compiled yet
    }

    // Use the NDFA's getNextSet method to see if there are any valid next states
    // from the starting node when consuming this character
    final Set<NDFANode> nextStates = myNode.getNextSet(ch);
    return !nextStates.isEmpty();
  }
}
