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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.utils.CounterType;
import no.rmz.rmatch.utils.FastCounter;
import no.rmz.rmatch.utils.FastCounters;

/**
 * A an implementation of the MatchSet interface. A MatchSet tracks all potential matches that start
 * from the same location in the input.
 *
 * <p>Lazy materialization: the set of regexps still alive for this start position is exactly the
 * regexp set of the current DFA node (DFA node regexp sets shrink monotonically along any
 * transition path), so no per-regexp bookkeeping is needed while a potential match is merely
 * "alive". A {@link Match} object is created only when a regexp first reaches a terminal state —
 * the only point where observable state (a committable match) exists. Speculative candidates that
 * die before ever reaching a terminal state never allocate anything.
 */
public final class MatchSetImpl implements MatchSet {
  /** A counter for MatchSetImpls. */
  private static final FastCounter MY_COUNTER = FastCounters.newCounter(CounterType.MATCH_SET_IMPL);

  /**
   * Commit this match relative to a bunch of other matches.
   *
   * <p>Now committing simply means adding this match to a collection of matches given as
   * parameters.
   *
   * <p>However, the current match is only added to the collection of runnable matches if it's
   * dominating the regular expression it's representing
   *
   * <p>If the current match is dominating its regular expression, then add it to the set of
   * runnable matches given as parameter.
   *
   * <p>This method is public only to facilitate testing. It's not part of any interface and
   * shouldn't be used directly anywhere.
   *
   * @param m the match to commit.
   * @param runnableMatches a collector of runnable matches
   */
  public static void commitMatch(final Match m, final RunnableMatchesHolder runnableMatches) {
    assert (!m.isActive());
    assert (m.isFinal());

    final boolean isDominating = m.getRegexp().isDominating(m);
    final boolean isStronglyDominating = m.getRegexp().isStronglyDominated(m);

    if (isDominating && !isStronglyDominating) {
      runnableMatches.add(m);
      m.getMatchSet().removeMatch(m);
    }
  }

  @Override
  public int hashCode() {
    return Long.hashCode(getId());
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof MatchSet ms) {
      return ms.getId() == getId();
    } else {
      return false;
    }
  }

  /**
   * Matches that have reached a terminal state at least once, keyed by regexp. Only these carry
   * observable state; merely-alive candidates are represented implicitly by the current DFA node's
   * regexp set.
   */
  private final Map<Regexp, Match> materialized = new HashMap<>(4);

  /** Reusable list for iteration snapshots to avoid repeated allocations in hot paths. */
  private final List<Match> matchSnapshot = new ArrayList<>();

  /** The current deterministic node that is used when pushing the matches further. */
  private DFANode currentNode;

  /**
   * The regexps that are candidates for matches starting at this position. Materialization is
   * restricted to this set so that pre-filtered (e.g. prefilter-narrowed) candidate sets behave
   * exactly as if only those regexps had been tracked from the start.
   */
  private final Set<Regexp> candidates;

  /**
   * Maximum candidate-set size for which the exact per-step liveness test is performed. For
   * candidate sets larger than this, the set provably contains the DFA node's alive set from the
   * second character onward (start filters only remove regexps that cannot survive character two),
   * so the match set dies exactly when the DFA path dies and no per-step test is needed. Small
   * sets, however, are typically prefilter-narrowed and can die long before the DFA path does —
   * without this test the match set would keep stepping a long-lived DFA path for nothing
   * (observed: 30x slowdown on the prefiltered stable-10K gate workload).
   */
  private static final int SMALL_CANDIDATE_LIVENESS_LIMIT = 8;

  /** Candidates to liveness-test each step, or null when the test is unnecessary (large sets). */
  private final Set<Regexp> smallCandidates;

  /** The start position of all the matches associated with this MatchSetImpl. */
  private final int start;

  /** An identifier uniquely identifying this MatchSetImpl among other MatchSetImpl instances. */
  private final long id;

  /**
   * Create a new MatchSetImpl.
   *
   * @param startIndex The start position in the input.
   * @param newCurrentNode The deterministic start node to start with.
   */
  public MatchSetImpl(final int startIndex, final DFANode newCurrentNode) {
    this(startIndex, newCurrentNode, null);
  }

  /**
   * Create a new MatchSetImpl with character-based optimization.
   *
   * @param startIndex The start position in the input.
   * @param newCurrentNode The deterministic start node to start with.
   * @param currentChar The current character being processed (for optimization). If null, all
   *     regexps will be considered.
   */
  public MatchSetImpl(
      final int startIndex, final DFANode newCurrentNode, final Character currentChar) {
    this(startIndex, newCurrentNode, currentChar, null);
  }

  /**
   * Create a new MatchSetImpl with pre-computed candidate regexps (performance optimization).
   *
   * @param startIndex The start position in the input.
   * @param newCurrentNode The deterministic start node to start with.
   * @param currentChar The current character being processed.
   * @param preComputedCandidates Pre-filtered regexps that can start with currentChar. If null,
   *     filtering will be computed.
   */
  public MatchSetImpl(
      final int startIndex,
      final DFANode newCurrentNode,
      final Character currentChar,
      final Set<Regexp> preComputedCandidates) {
    checkNotNull(newCurrentNode, "newCurrentNode can't be null");
    checkArgument(startIndex >= 0, "Start index can't be negative");
    this.currentNode = newCurrentNode;
    start = startIndex;
    id = MY_COUNTER.inc();

    if (preComputedCandidates != null) {
      candidates = preComputedCandidates;
    } else if (currentChar != null) {
      candidates = this.currentNode.getRegexpsThatCanStartWith(currentChar);
    } else {
      candidates = this.currentNode.getRegexps();
    }

    smallCandidates = candidates.size() <= SMALL_CANDIDATE_LIVENESS_LIMIT ? candidates : null;

    // Materialize matches only for regexps that are final already at the first character
    // (length-1 matches). Everything else stays implicit until it reaches a terminal state.
    if (!candidates.isEmpty()) {
      materializeNewlyTerminal(startIndex);
    } else {
      currentNode = null; // Nothing can ever match from here.
    }
  }

  /**
   * Create Match objects for candidate regexps that are terminal at the current node and not yet
   * materialized. Such a match is created final and active, ending at the current position.
   */
  private void materializeNewlyTerminal(final int currentPos) {
    final Set<Regexp> terminals = terminalRegexpsFor(currentNode);
    if (terminals.isEmpty()) {
      return;
    }
    for (final Regexp r : terminals) {
      if (!materialized.containsKey(r) && candidates.contains(r)) {
        final Match m = new MatchImpl(this, r, true);
        m.setEnd(currentPos);
        materialized.put(r, m);
      }
    }
  }

  @Override
  public int getStart() {
    return start;
  }

  @Override
  public Set<Match> getMatches() {
    return Set.copyOf(materialized.values());
  }

  @Override
  public boolean hasMatches() {
    // The match set is worth keeping while it has committable matches, or while the DFA path is
    // still alive (some candidate may yet reach a terminal state). This slightly over-approximates
    // the old behaviour for prefilter-narrowed candidate sets (the DFA path may outlive the
    // narrowed candidates), which costs at most a few extra DFA steps and changes no output.
    return !materialized.isEmpty() || currentNode != null;
  }

  /**
   * Progress one, if any matches are inactivated they are removed from the match set. If they have
   * something to contribute they are committed to runnableMatches too.
   *
   * @param ns A NodeStorage used to find new nodes.
   * @param currentChar The current character.
   * @param currentPos The current position.
   * @param runnableMatches a container for runnable matches. Matches will be put here if they can
   *     be run, possibly, pending domination stuff.
   */
  @Override
  public void progress(
      final NodeStorage ns,
      final Character currentChar,
      final int currentPos,
      final RunnableMatchesHolder runnableMatches) {

    // Hot path: called once per input character per active match set. Internal invariants
    // (non-null arguments, non-negative position) are guaranteed by the engine loop, so no
    // precondition checks here.

    if (currentNode == null) {
      return;
    }

    currentNode = currentNode.getNext(currentChar, ns);

    if (currentNode == null) {
      terminateAssociatedMatches(currentChar, runnableMatches);
      return;
    }

    // Check if there are any regexps for which matches must fail at this node, and fail them.
    if (!materialized.isEmpty() && currentNode.failsSomeRegexps()) {
      failMatchesThatCannotContinue(currentChar);
    }

    // Progress matches that have observable state.
    if (!materialized.isEmpty()) {
      progressMaterializedMatches(currentPos, runnableMatches, currentChar);
    }

    // Materialize matches for regexps that just reached a terminal state.
    materializeNewlyTerminal(currentPos);

    // Exact liveness test for narrow (typically prefilter-mapped) candidate sets: when no
    // candidate is alive at the current node and nothing is materialized, this match set can
    // never produce anything again — stop stepping the DFA path.
    if (smallCandidates != null && materialized.isEmpty()) {
      final Set<Regexp> alive = currentNode.getRegexps();
      boolean anyAlive = false;
      for (final Regexp r : smallCandidates) {
        if (alive.contains(r)) {
          anyAlive = true;
          break;
        }
      }
      if (!anyAlive) {
        currentNode = null;
      }
    }
  }

  private void terminateAssociatedMatches(
      final Character currentChar, final RunnableMatchesHolder runnableMatches) {
    // Found no nodes going out of the current node: commit what is final, abandon the rest.
    if (materialized.isEmpty()) {
      return;
    }
    matchSnapshot.clear();
    matchSnapshot.addAll(materialized.values());

    for (final Match m : matchSnapshot) {
      m.setInactive();
      if (m.isFinal()) {
        commitMatch(m, runnableMatches);
        if (!m.isAbandoned()) {
          m.abandon(currentChar);
        }
      }
      removeMatch(m);
    }
  }

  private void progressMaterializedMatches(
      final int currentPos,
      final RunnableMatchesHolder runnableMatches,
      final Character currentChar) {

    final Set<Regexp> activeRegexpsAtNode = currentNode.getRegexps();
    final Set<Regexp> terminalRegexpsAtNode = terminalRegexpsFor(currentNode);

    matchSnapshot.clear();
    matchSnapshot.addAll(materialized.values());

    for (final Match m : matchSnapshot) {
      final Regexp regexp = m.getRegexp();
      final boolean isActive = activeRegexpsAtNode.contains(regexp);

      m.setActive(isActive);

      if (isActive) {
        // Advance the match: extend its end and update finality for this position.
        m.setEnd(currentPos);
        m.setFinal(terminalRegexpsAtNode.contains(regexp));
      } else {
        // The regexp fell out of the alive set: commit if it ended final, then abandon.
        if (m.isFinal()) {
          commitMatch(m, runnableMatches);
        }
        if (!m.isAbandoned()) {
          m.abandon(currentChar);
        }
        removeMatch(m);
      }
    }
  }

  private void failMatchesThatCannotContinue(final Character currentChar) {
    matchSnapshot.clear();
    matchSnapshot.addAll(materialized.values());

    for (final Match m : matchSnapshot) {
      if (currentNode.isFailingFor(m.getRegexp())) {
        m.abandon(currentChar);
        removeMatch(m);
      }
    }
  }

  @Override
  public void removeMatch(final Match m) {
    checkNotNull(m);
    m.getRegexp().abandonMatchSet(this);
    materialized.remove(m.getRegexp(), m);
  }

  @Override
  public void finalCommit(final RunnableMatchesHolder runnableMatches) {
    checkNotNull(runnableMatches, "Target can't be null");

    if (materialized.isEmpty()) {
      return;
    }

    matchSnapshot.clear();
    matchSnapshot.addAll(materialized.values());

    // Pre-size with reasonable capacity based on expected regexp count
    final Set<Regexp> visitedRegexps = new HashSet<>(matchSnapshot.size());

    for (final Match m : matchSnapshot) {
      if (m.notReadyForCommit()) {
        continue;
      }
      final Regexp r = m.getRegexp();
      if (!visitedRegexps.contains(r)) {
        visitedRegexps.add(r);
        r.commitUndominated(runnableMatches);
      }
      removeMatch(m);
    }
  }

  @Override
  public long getId() {
    return id;
  }

  /**
   * The set of regexps for which the given node is terminal. Uses the cached set on DFANodeImpl
   * when available; falls back to per-regexp probing for other DFANode implementations (mocks in
   * tests).
   */
  private static Set<Regexp> terminalRegexpsFor(final DFANode dfaNode) {
    if (dfaNode instanceof DFANodeImpl impl) {
      return impl.getTerminalRegexpsCached();
    }
    final Set<Regexp> result = new HashSet<>();
    for (final Regexp r : dfaNode.getRegexps()) {
      if (dfaNode.isTerminalFor(r)) {
        result.add(r);
      }
    }
    return result;
  }
}
