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

import java.util.Comparator;
import java.util.Set;

/**
 * Engine-internal group of match candidates that share the same input start position.
 *
 * <p>This interface is public for historical and diagnostic reasons. It is not part of the normal
 * application API; applications should register {@link Action} callbacks with {@link Matcher}
 * instead of interacting with {@code MatchSet} directly.
 */
public interface MatchSet {

  /**
   * Commit any final, undominated matches from this set to the supplied target.
   *
   * @param target the recipient of matches that are committed.
   */
  void finalCommit(final RunnableMatchesHolder target);

  /**
   * Return the matches currently associated with this set.
   *
   * @return a set of Match instances.
   */
  Set<Match> getMatches();

  /**
   * The start position for all the matches in the input.
   *
   * @return a start position.
   */
  int getStart();

  /**
   * True if there are any matches in this MatchSet.
   *
   * @return do we have any matches?
   */
  boolean hasMatches();

  /**
   * An identifier that uniquely identifies the match set.
   *
   * @return an id.
   */
  long getId();

  /**
   * Progress this match set one character ahead.
   *
   * <p>Matches that can continue are advanced, matches that cannot continue are abandoned, and
   * matches that can validly terminate are staged in {@code runnableMatches}. If several matches
   * overlap for the same expression, domination rules decide which callbacks are eventually run.
   *
   * @param ns A node storage instance used to get new DFA nodes.
   * @param currentChar The current char.
   * @param currentPos The current position.
   * @param runnableMatches The set of runnable matches.
   */
  void progress(
      final NodeStorage ns,
      final Character currentChar,
      final int currentPos,
      final RunnableMatchesHolder runnableMatches);

  /**
   * Context-aware progress used when zero-width assertions are present.
   *
   * @param ns A node storage instance used to get new DFA nodes.
   * @param currentChar The current char.
   * @param currentPos The current position.
   * @param runnableMatches The set of runnable matches.
   * @param context positional context for assertions adjacent to this transition
   */
  void progress(
      final NodeStorage ns,
      final Character currentChar,
      final int currentPos,
      final RunnableMatchesHolder runnableMatches,
      final MatchContext context);

  /**
   * Remove a match from the MatchSet instance.
   *
   * @param m a match.
   */
  void removeMatch(final Match m);

  /** A comparator for match sets. Match sets are equal iff they have the same identifier. */
  Comparator<MatchSet> COMPARE_BY_ID =
      (final MatchSet t, final MatchSet t1) -> {
        final long l1 = t.getId();
        final long l2 = t1.getId();

        return Long.compare(l1, l2);
      };
}
