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

import no.rmz.rmatch.Action;
import no.rmz.rmatch.Buffer;
import no.rmz.rmatch.Matcher;

/**
 * Engine-internal compiled regular-expression state.
 *
 * <p>This type is public because the matcher implementation and older diagnostic tools expose it.
 * Application code should normally treat patterns as strings passed to {@link Matcher#add(String,
 * Action)}. Implementing or mutating {@code Regexp} directly is not required for ordinary rmatch
 * use.
 */
public interface Regexp extends Comparable<Regexp> {

  /**
   * Remove all references from this expression to a match candidate.
   *
   * @param m match candidate to abandon
   * @param currentChar character being processed when abandonment occurs
   */
  void abandonMatch(final Match m, final Character currentChar);

  /**
   * Associate an action with this expression.
   *
   * @param a action to run when this expression matches
   */
  void add(final Action a);

  /**
   * Mark a node as active for this expression.
   *
   * @param n node that may continue candidates for this expression
   */
  // XXX This should be DFA nodes. It never happens for NDFAs.
  void addActive(final Node n);

  /**
   * Mark a node as terminal for this expression.
   *
   * @param n node that may complete candidates for this expression
   */
  void addTerminalNode(final Node n); // Can be both N and D FA.

  /**
   * Add final, undominated candidates for this expression to the runnable set.
   *
   * @param runnableMatches recipient for candidates whose actions may be run
   */
  void commitUndominated(final RunnableMatchesHolder runnableMatches);

  /**
   * Remove this expression's references to a match set and its candidates.
   *
   * @param ms match set to abandon
   */
  void abandonMatchSet(final MatchSet ms);

  /**
   * Return the start node of this expression's compiled NDFA.
   *
   * @return compiled NDFA start node
   */
  NDFANode getMyNode();

  /**
   * Return the pattern text used to create this expression.
   *
   * @return original regular-expression string
   */
  String getRexpString();

  /**
   * Return whether this expression has any registered actions.
   *
   * <p>An expression without actions has no observable application effect and can often be ignored
   * by the matcher.
   *
   * @return {@code true} if at least one action is registered
   */
  boolean hasActions();

  /**
   * Return whether this expression currently has active match candidates.
   *
   * @return {@code true} if one or more candidates are associated with this expression
   */
  boolean hasMatches(); // XXX  Bogus?

  /**
   * Return whether a node may continue candidates for this expression.
   *
   * @param n a node
   * @return {@code true} if {@code n} is active for this expression
   */
  boolean isActiveFor(final Node n);

  /**
   * Return whether this expression has been compiled to an NDFA.
   *
   * @return {@code true} after compilation has produced an NDFA start node
   */
  boolean isCompiled();

  /**
   * Return whether a candidate dominates all competing candidates for this expression.
   *
   * @param m the match
   * @return {@code true} if {@code m} is the dominating candidate
   */
  boolean isDominating(final Match m);

  /**
   * Return whether a node is terminal for this expression's NDFA.
   *
   * @param n the node.
   * @return {@code true} if {@code n} can complete this expression
   */
  boolean hasTerminalNdfaNode(final Node n);

  /**
   * Return whether a candidate is strongly dominated by another candidate.
   *
   * <p>A strongly dominated candidate cannot produce an action because an overlapping candidate for
   * the same expression has priority.
   *
   * @param m a match
   * @return {@code true} if {@code m} should not be committed
   */
  boolean isStronglyDominated(Match m);

  /**
   * Run this expression's actions for a completed match.
   *
   * <p>The offsets use the engine-internal inclusive convention. Implementations are responsible
   * for delivering the public half-open {@code [start, end)} convention to {@link
   * no.rmz.rmatch.Action} callbacks.
   *
   * @param b the buffer
   * @param start zero-based inclusive start offset
   * @param end zero-based inclusive end offset
   */
  void performActions(final Buffer b, final int start, final int end);

  /**
   * Register a match with this regexp.
   *
   * @param m match candidate to associate with this expression
   */
  void registerMatch(final Match m);

  /**
   * Remove an action from this regexp.
   *
   * @param a action to remove
   */
  void remove(final Action a);

  /**
   * Store the start node produced when this expression is compiled.
   *
   * @param myNode compiled NDFA start node
   */
  void setMyNDFANode(final NDFANode myNode);

  /**
   * Return whether this expression is associated with a particular candidate.
   *
   * @param m a match
   * @return {@code true} if {@code m} is registered with this expression
   */
  boolean hasMatch(final Match m);

  /**
   * Check if this regexp can start with the given character. This method considers both direct
   * character matches and epsilon transitions from the start node.
   *
   * @param ch the character to check
   * @return {@code true} if the expression can start with {@code ch}
   */
  boolean canStartWith(final Character ch);

  /**
   * Context-aware first-character check used for zero-width assertions.
   *
   * @param ch the character to check
   * @param context positional context for assertions adjacent to this transition
   * @return {@code true} if the expression can start with the character in this context
   */
  boolean canStartWith(final Character ch, final MatchContext context);

  /** Mark this expression as using context-sensitive zero-width assertions. */
  void markUsesContextAssertions();

  /** Return true when this expression uses context-sensitive zero-width assertions. */
  boolean usesContextAssertions();
}
