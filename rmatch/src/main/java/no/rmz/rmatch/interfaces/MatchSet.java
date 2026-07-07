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
   * @param target recipient for committed matches
   */
  void finalCommit(final RunnableMatchesHolder target);

  /**
   * Return the matches currently associated with this set.
   *
   * @return current match candidates
   */
  Set<Match> getMatches();

  /**
   * Return the input position where this match set began.
   *
   * @return zero-based start position
   */
  int getStart();

  /**
   * Return whether this set currently contains any match candidates.
   *
   * @return {@code true} if at least one candidate is present
   */
  boolean hasMatches();

  /**
   * Return the matcher-local identifier for this match set.
   *
   * @return unique match-set identifier
   */
  long getId();

  /**
   * Progress this match set one character ahead.
   *
   * <p>Matches that can continue are advanced, matches that cannot continue are abandoned, and
   * matches that can validly terminate are staged in {@code runnableMatches}. If several matches
   * overlap for the same expression, domination rules decide which callbacks are eventually run.
   *
   * @param ns node storage used to create or reuse DFA nodes
   * @param currentChar character currently being consumed
   * @param currentPos zero-based input position of {@code currentChar}
   * @param runnableMatches recipient for matches that become runnable
   */
  void progress(
      final NodeStorage ns,
      final Character currentChar,
      final int currentPos,
      final RunnableMatchesHolder runnableMatches);

  /**
   * Context-aware progress used when zero-width assertions are present.
   *
   * @param ns node storage used to create or reuse DFA nodes
   * @param currentChar character currently being consumed
   * @param currentPos zero-based input position of {@code currentChar}
   * @param runnableMatches recipient for matches that become runnable
   * @param context positional context for assertions adjacent to this transition
   */
  void progress(
      final NodeStorage ns,
      final Character currentChar,
      final int currentPos,
      final RunnableMatchesHolder runnableMatches,
      final MatchContext context);

  /**
   * Remove a candidate from this match set.
   *
   * @param m candidate to remove
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
