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

import java.util.*;
import no.rmz.rmatch.engine.prefilter.AhoCorasickPrefilter;
import no.rmz.rmatch.engine.prefilter.LiteralHint;
import no.rmz.rmatch.engine.prefilter.LiteralPrefilter;
import no.rmz.rmatch.interfaces.*;

/**
 * An implementation of a MatchEngine that can be used to match regular expressions against input.
 */
public final class MatchEngineImpl implements MatchEngine {
  /**
   * Perform matches by triggering the relevant actions.
   *
   * @param b The buffer we are reading matches from.
   * @param matches The collection of matching we are performing.
   * @param bePermissive If permissive, set all the matches to be inactive and abandoned, otherwise
   *     expect them to be inactive and abandoned prior to invocation.
   */
  private static void performMatches(
      final Buffer b, final Collection<Match> matches, final Boolean bePermissive) {
    checkNotNull(matches);
    checkNotNull(b);
    for (final Match match : matches) {
      if (bePermissive) {
        match.setInactive();
      }
      performMatch(b, match);
      if (bePermissive) {
        match.abandon(null);
      }
    }
  }

  /**
   * Perform actions associated with a match.
   *
   * @param b A buffer where the matched content is located.
   * @param m The match to be performed.
   */
  private static void performMatch(final Buffer b, final Match m) {
    checkNotNull(m);
    checkNotNull(b);
    if (m.isFinal()) {
      final int start = m.getStart();
      final int end = m.getEnd();
      final Regexp regexp = m.getRegexp();

      regexp.performActions(b, start, end);
    }
  }

  /**
   * The instance that is used to map sets of NDFA nodes to NDFA nodes, and if necessarily create
   * new DFA nodes.
   */
  private final NodeStorage ns;

  /** Optional prefilter for reducing regex invocations (null if disabled). */
  private AhoCorasickPrefilter prefilter;

  /** Whether prefiltering is enabled via system property. */
  private final boolean prefilterEnabled;

  /** Set of positions where matches should be started (when using prefilter). */
  private Set<Integer> candidatePositions;

  /**
   * Implements the MatchEngine interface, uses a particular NodeStorage instance.
   *
   * @param ns non null NodeStorage instance.
   */
  public MatchEngineImpl(final NodeStorage ns) {
    this.ns = checkNotNull(ns, "NodeStorage can't be null");
    this.prefilterEnabled = "aho".equalsIgnoreCase(System.getProperty("rmatch.prefilter", "off"));
  }

  /**
   * Configures the prefilter with pattern information.
   *
   * <p>This method should be called whenever patterns are added or changed to update the prefilter.
   * If prefiltering is disabled via system property, this method does nothing.
   *
   * @param patterns map from pattern ID to pattern string
   * @param flags map from pattern ID to regex flags
   */
  public void configurePrefilter(
      final Map<Integer, String> patterns, final Map<Integer, Integer> flags) {
    if (!prefilterEnabled || patterns.isEmpty()) {
      prefilter = null;
      return;
    }

    final List<LiteralHint> hints = new ArrayList<>();

    for (final Map.Entry<Integer, String> entry : patterns.entrySet()) {
      final int patternId = entry.getKey();
      final String regex = entry.getValue();
      final int regexFlags = flags.getOrDefault(patternId, 0);

      final Optional<LiteralHint> hint = LiteralPrefilter.extract(patternId, regex, regexFlags);
      if (hint.isPresent()) {
        hints.add(hint.get());
      }
    }

    if (!hints.isEmpty()) {
      prefilter = new AhoCorasickPrefilter(hints);
    } else {
      prefilter = null;
    }
  }

  /**
   * Progress the matcher one step.
   *
   * @param b The buffer we are reading characters from.
   * @param currentChar The current character.
   * @param currentPos The current position.
   * @param activeMatchSets The set of active match sets.
   */
  private void matcherProgress(
      final Buffer b,
      final Character currentChar,
      final int currentPos,
      final Set<MatchSet> activeMatchSets) {

    checkNotNull(currentChar, "currentChar can't be null");
    checkArgument(currentPos >= 0, "Pos in buf must be non-negative");

    // Progress all the already active matches and collect
    // the runnables.  The runnables may or may not be
    // active when they run, but they must be final.
    final RunnableMatchesHolder runnableMatches = new RunnableMatchesHolderImpl();

    if (!activeMatchSets.isEmpty()) {
      final Set<MatchSet> setsToRemove = new HashSet<>();
      for (final MatchSet ms : activeMatchSets) {
        ms.progress(ns, currentChar, currentPos, runnableMatches);
        if (!ms.hasMatches()) {
          setsToRemove.add(ms);
        }
      }
      activeMatchSets.removeAll(setsToRemove);
    }

    // If the current input character opened up a possibility of new
    // matches, then by all means make a new match set to represent
    // that fact.
    boolean shouldStartMatch = true;

    // Use prefilter to decide whether to start matches at this position
    if (prefilterEnabled && candidatePositions != null) {
      shouldStartMatch = candidatePositions.contains(currentPos);
    }

    if (shouldStartMatch) {
      final DFANode startOfNewMatches = ns.getNextFromStartNode(currentChar);
      if (startOfNewMatches != null) {
        final MatchSet ms;
        ms = new MatchSetImpl(currentPos, startOfNewMatches, currentChar);
        if (ms.hasMatches()) {
          activeMatchSets.add(ms);
        }
      }
    }

    // Then run through all the active match sets to see
    // if there are any
    // matches that should be commited.  When a matchSet is fresh out
    // of active matches it should be snuffed.

    for (final MatchSet ms : activeMatchSets) {

      // Collect runnable matches into the runnableMatches
      // instance.
      ms.finalCommit(runnableMatches);

      // If there are no matches in this match set, then we shouldn't
      // consider it any further.
      if (!ms.hasMatches()) {
        activeMatchSets.remove(ms);
      }
    }

    // Run through all the runnable matches and perform actions.
    // (Don't be permissive by default, hence "false").
    performMatches(b, runnableMatches.getMatches(), false);
  }

  @Override
  public void match(final Buffer b) {
    checkNotNull(b, "Buffer can't be null");

    final Set<MatchSet> activeMatchSets;
    activeMatchSets = Collections.synchronizedSet(new TreeSet<>(MatchSet.COMPARE_BY_ID));

    // If prefiltering is enabled, we need to collect the input first
    final String fullText = prefilterEnabled ? collectBufferText(b) : null;
    if (prefilterEnabled && prefilter != null && fullText != null) {
      runPrefilterScan(fullText);
    }

    // Reset buffer position to start
    // NOTE: This assumes we can reset the buffer position, which works with StringBuffer
    if (fullText != null && b instanceof no.rmz.rmatch.utils.StringBuffer) {
      ((no.rmz.rmatch.utils.StringBuffer) b).setCurrentPos(-1);
    }

    // Advance all match sets forward one character.
    while (b.hasNext()) {
      final Character nextChar = b.getNext();
      final int currentPos = b.getCurrentPos();
      matcherProgress(b, nextChar, currentPos, activeMatchSets);
    }

    // Handle the stragglers
    for (final MatchSet ms : activeMatchSets) {
      // Be permissive when handling stragglers
      performMatches(b, ms.getMatches(), true);
    }

    activeMatchSets.clear();
  }

  /** Collects the full text from the buffer for prefilter scanning. */
  private String collectBufferText(final Buffer b) {
    if (!prefilterEnabled || prefilter == null) {
      return null;
    }

    final StringBuilder sb = new StringBuilder();
    while (b.hasNext()) {
      sb.append(b.getNext());
    }
    return sb.toString();
  }

  /** Runs the prefilter scan to identify candidate positions. */
  private void runPrefilterScan(final String text) {
    if (text == null || prefilter == null) {
      candidatePositions = null;
      return;
    }

    // Run prefilter
    final List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan(text);

    // Convert to set of candidate positions
    candidatePositions = new HashSet<>();
    for (final AhoCorasickPrefilter.Candidate candidate : candidates) {
      final int startPos = candidate.startIndexForMatch();
      if (startPos >= 0) {
        candidatePositions.add(startPos);
      }
    }
  }
}
