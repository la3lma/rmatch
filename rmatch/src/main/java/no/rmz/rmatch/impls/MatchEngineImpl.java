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

import no.rmz.rmatch.engine.prefilter.AhoCorasickPrefilter;
import no.rmz.rmatch.engine.prefilter.LiteralHint;
import no.rmz.rmatch.engine.prefilter.LiteralPrefilter;
import no.rmz.rmatch.interfaces.*;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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

  /** Reusable collection for removing match sets to avoid repeated allocations. */
  private final Set<MatchSet> reusableSetsToRemove = new HashSet<>();

  /** Reusable collection for position-based filtering to avoid repeated allocations. */
  private final Set<Integer> reusableCandidatePositions = new HashSet<>();

  /** Reusable collection for regexp-to-position mapping to avoid repeated allocations. */
  private final Map<Integer, Set<Regexp>> reusablePositionToRegexps = new HashMap<>();

  /** Reusable runnable matches holder to avoid repeated allocations. */
  private final RunnableMatchesHolder reusableRunnableMatches = new RunnableMatchesHolderImpl();

  /** Whether prefiltering is enabled via system property. */
  private final boolean prefilterEnabled;

  /** Set of positions where matches should be started (when using prefilter). */
  private Set<Integer> candidatePositions;

  /** Map from positions to specific regexps that should be tested at those positions. */
  private Map<Integer, Set<Regexp>> positionToRegexps;

  /** Map from pattern IDs to Regexp objects for prefilter integration. */
  private Map<Integer, Regexp> patternIdToRegexp;

  /**
   * Implements the MatchEngine interface, uses a particular NodeStorage instance.
   *
   * @param ns non null NodeStorage instance.
   */
  public MatchEngineImpl(final NodeStorage ns) {
    this.ns = checkNotNull(ns, "NodeStorage can't be null");
    this.prefilterEnabled =
        "aho".equalsIgnoreCase(System.getProperty("rmatch.prefilter", "disabled"));
  }

  /**
   * Configures the prefilter with pattern information and Regexp mappings.
   *
   * <p>This method should be called whenever patterns are added or changed to update the prefilter.
   * If prefiltering is disabled via system property, this method does nothing.
   *
   * @param patterns map from pattern ID to pattern string
   * @param flags map from pattern ID to regex flags
   * @param regexpMappings map from pattern string to Regexp object
   */
  public void configurePrefilter(
      final Map<Integer, String> patterns,
      final Map<Integer, Integer> flags,
      final Map<String, Regexp> regexpMappings) {
    if (!prefilterEnabled || patterns.isEmpty()) {
      prefilter = null;
      patternIdToRegexp = null;
      return;
    }

    final List<LiteralHint> hints = new ArrayList<>();
    patternIdToRegexp = new HashMap<>();

    for (final Map.Entry<Integer, String> entry : patterns.entrySet()) {
      final int patternId = entry.getKey();
      final String regex = entry.getValue();
      final int regexFlags = flags.getOrDefault(patternId, 0);

      // Store pattern ID to Regexp mapping
      final Regexp regexp = regexpMappings.get(regex);
      if (regexp != null) {
        patternIdToRegexp.put(patternId, regexp);
      }

      final Optional<LiteralHint> hint = LiteralPrefilter.extract(patternId, regex, regexFlags);
      hint.ifPresent(hints::add);
    }

    if (!hints.isEmpty()) {
      prefilter = new AhoCorasickPrefilter(hints);
    } else {
      prefilter = null;
      patternIdToRegexp = null;
    }
  }

  /** Legacy method for backwards compatibility - delegates to the enhanced version. */
  public void configurePrefilter(
      final Map<Integer, String> patterns, final Map<Integer, Integer> flags) {
    // Without regexp mappings, prefilter can't map candidates back to regexps
    // This is a limitation of the legacy interface
    configurePrefilter(patterns, flags, new HashMap<>());
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
    // Clear and reuse the runnable matches holder to avoid allocation
    ((RunnableMatchesHolderImpl) reusableRunnableMatches).clear();
    final RunnableMatchesHolder runnableMatches = reusableRunnableMatches;

    if (!activeMatchSets.isEmpty()) {
      // Reuse collection to avoid repeated allocations
      reusableSetsToRemove.clear();
      for (final MatchSet ms : activeMatchSets) {
        ms.progress(ns, currentChar, currentPos, runnableMatches);
        if (!ms.hasMatches()) {
          reusableSetsToRemove.add(ms);
        }
      }
      activeMatchSets.removeAll(reusableSetsToRemove);
    }

    // If the current input character opened up a possibility of new
    // matches, then by all means make a new match set to represent
    // that fact.
    boolean shouldStartMatch = true;

    // Use prefilter to decide whether to start matches at this position
    // Only check prefilter if it's enabled and configured
    if (prefilterEnabled && prefilter != null && candidatePositions != null) {
      shouldStartMatch = candidatePositions.contains(currentPos);
    } else if (prefilterEnabled && prefilter != null) {
      // If prefilter is configured but no candidates found, don't start matches
      shouldStartMatch = false;
    }

    if (shouldStartMatch) {
      final DFANode startOfNewMatches = ns.getNextFromStartNode(currentChar);
      if (startOfNewMatches != null) {
        Set<Regexp> candidateRegexps;

        // OPTIMIZATION: Use prefilter-specific regexps if available
        if (prefilterEnabled
            && prefilter != null
            && positionToRegexps != null
            && positionToRegexps.containsKey(currentPos)) {
          // Use only the regexps identified by the prefilter for this position
          candidateRegexps = positionToRegexps.get(currentPos);
        } else {
          // Fallback: Check if any regexps can start with this character BEFORE creating MatchSet
          candidateRegexps = startOfNewMatches.getRegexpsThatCanStartWith(currentChar);
        }

        if (!candidateRegexps.isEmpty()) {
          // Pass pre-computed candidates to avoid redundant filtering in MatchSetImpl
          final MatchSet ms =
              new MatchSetImpl(currentPos, startOfNewMatches, currentChar, candidateRegexps);
          if (ms.hasMatches()) {
            activeMatchSets.add(ms);
          }
        }
      }
    }

    // Then run through all the active match sets to see
    // if there are any matches that should be commited.
    // Use iterator to safely remove empty match sets during iteration.

    final Iterator<MatchSet> msIterator = activeMatchSets.iterator();
    while (msIterator.hasNext()) {
      final MatchSet ms = msIterator.next();

      // Collect runnable matches into the runnableMatches instance.
      ms.finalCommit(runnableMatches);

      // If there are no matches in this match set, remove it safely
      if (!ms.hasMatches()) {
        msIterator.remove();
      }
    }

    // Run through all the runnable matches and perform actions.
    // (Don't be permissive by default, hence "false").
    performMatches(b, runnableMatches.getMatches(), false);
  }

  @Override
  public void match(final Buffer b) {
    checkNotNull(b, "Buffer can't be null");

    // Use plain HashSet instead of synchronized TreeSet for better performance
    // Synchronization is unnecessary since match() is not called concurrently
    final Set<MatchSet> activeMatchSets = new HashSet<>();

    // Only do prefiltering work if actually enabled and configured
    if (prefilterEnabled && prefilter != null) {
      final String fullText = collectBufferText(b);
      if (fullText != null) {
        runPrefilterScan(fullText);
        // Note: collectBufferText() doesn't consume the buffer, so no reset needed
      }
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

  /** Collects the full text from the buffer for prefilter scanning without consuming it. */
  private String collectBufferText(final Buffer b) {
    // This method should only be called when prefilter is actually configured
    assert prefilterEnabled && prefilter != null;

    // For StringBuffer, we can access the content directly without consuming the buffer
    if (b instanceof no.rmz.rmatch.utils.StringBuffer) {
      final no.rmz.rmatch.utils.StringBuffer sb = (no.rmz.rmatch.utils.StringBuffer) b;
      return sb.getCurrentRestString();
    }

    // For other buffer types, we need to consume and restore, but this is not safe
    // For now, disable prefiltering for non-StringBuffer types
    return null;
  }

  /** Runs the prefilter scan to identify candidate positions. */
  private void runPrefilterScan(final String text) {
    if (text == null || prefilter == null) {
      candidatePositions = null;
      positionToRegexps = null;
      return;
    }

    // Run prefilter
    final List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan(text);

    // Reuse collections instead of allocating new ones
    reusableCandidatePositions.clear();
    reusablePositionToRegexps.clear();

    for (final AhoCorasickPrefilter.Candidate candidate : candidates) {
      final int startPos = candidate.startIndexForMatch();
      if (startPos >= 0) {
        reusableCandidatePositions.add(startPos);

        // Map pattern ID to an actual Regexp object
        if (patternIdToRegexp != null) {
          final Regexp regexp = patternIdToRegexp.get(candidate.patternId);
          if (regexp != null) {
            reusablePositionToRegexps.computeIfAbsent(startPos, k -> new HashSet<>()).add(regexp);
          }
        }
      }
    }

    // Set the references to point to our reusable collections
    candidatePositions = reusableCandidatePositions;
    positionToRegexps = reusablePositionToRegexps;
  }
}
