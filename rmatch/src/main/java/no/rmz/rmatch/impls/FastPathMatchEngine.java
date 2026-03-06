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
import no.rmz.rmatch.engine.fastpath.AsciiOptimizer;
import no.rmz.rmatch.engine.fastpath.StateSetBuffers;
import no.rmz.rmatch.engine.prefilter.AhoCorasickPrefilter;
import no.rmz.rmatch.engine.prefilter.LiteralHint;
import no.rmz.rmatch.engine.prefilter.LiteralPrefilter;
import no.rmz.rmatch.interfaces.*;

/**
 * Enhanced MatchEngine with fast-path optimizations for common cases.
 *
 * <p>This implementation integrates:
 *
 * <ul>
 *   <li>ASCII fast-lane: optimized character classification for ASCII input
 *   <li>State-set buffer reuse: thread-local scratch buffers to reduce allocations
 *   <li>Prefix filtering: AhoCorasick prefilter for literal substrings
 * </ul>
 *
 * <p>Enable via system property: {@code -Drmatch.engine=fastpath}
 */
public final class FastPathMatchEngine implements MatchEngine {
  private static final int[] EMPTY_INT_ARRAY = new int[0];

  /** The NodeStorage instance. */
  private final NodeStorage ns;

  /** Optional AhoCorasick prefilter (null if disabled). */
  private AhoCorasickPrefilter prefilter;

  /** Map from pattern IDs to Regexp objects. */
  private Map<Integer, Regexp> patternIdToRegexp;

  /** Whether prefiltering is enabled. */
  private final boolean prefilterEnabled;

  /**
   * Minimum pattern count threshold for prefilter activation. Default 5000 is optimal based on
   * testing.
   */
  private static final int PREFILTER_ACTIVATION_THRESHOLD =
      Integer.parseInt(System.getProperty("rmatch.prefilter.threshold", "5000"));

  /** Sorted positions where matches should be started (when using prefilter). */
  private int[] candidatePositions = EMPTY_INT_ARRAY;
  private int candidatePositionCursor;

  /** Sorted positions that have mapped regexps and the aligned regexp sets. */
  private int[] mappedRegexPositions = EMPTY_INT_ARRAY;
  private List<Set<Regexp>> mappedRegexps = Collections.emptyList();
  private int mappedRegexCursor;

  /**
   * Create a new FastPathMatchEngine.
   *
   * @param ns the NodeStorage instance
   */
  public FastPathMatchEngine(final NodeStorage ns) {
    this.ns = checkNotNull(ns, "NodeStorage can't be null");
    this.prefilterEnabled = "aho".equalsIgnoreCase(System.getProperty("rmatch.prefilter", "aho"));
  }

  /**
   * Configure the prefilter with pattern information.
   *
   * @param patterns map from pattern ID to pattern string
   * @param flags map from pattern ID to regex flags
   * @param regexpMappings map from pattern string to Regexp object
   */
  public void configurePrefilter(
      final Map<Integer, String> patterns,
      final Map<Integer, Integer> flags,
      final Map<String, Regexp> regexpMappings) {
    if (!prefilterEnabled
        || patterns.isEmpty()
        || patterns.size() < PREFILTER_ACTIVATION_THRESHOLD) {
      prefilter = null;
      patternIdToRegexp = null;
      return;
    }

    // Only enable prefilter when the pattern count is large enough.

    final List<LiteralHint> hints = new ArrayList<>(patterns.size());
    patternIdToRegexp = new HashMap<>(patterns.size());

    for (final Map.Entry<Integer, String> entry : patterns.entrySet()) {
      final int patternId = entry.getKey();
      final String regex = entry.getValue();
      final int regexFlags = flags.getOrDefault(patternId, 0);

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

  @Override
  public void match(final Buffer b) {
    checkNotNull(b, "Buffer can't be null");

    // Get thread-local buffers for state-set operations
    final StateSetBuffers buffers = StateSetBuffers.get();

    final Set<MatchSet> activeMatchSets = new HashSet<>();

    // Only activate prefiltering for this invocation if we can safely extract text.
    final boolean prefilterActive = preparePrefilterForMatch(b);

    // Get a reusable holder for runnable matches
    final RunnableMatchesHolder runnableMatches = new RunnableMatchesHolderImpl();

    // Main matching loop
    while (b.hasNext()) {
      final Character nextChar = b.getNext();
      final int currentPos = b.getCurrentPos();

      // Use ASCII fast-path if applicable
      if (AsciiOptimizer.isAscii(nextChar)) {
        matcherProgressAscii(
            b, nextChar, currentPos, activeMatchSets, runnableMatches, prefilterActive);
      } else {
        matcherProgressUnicode(
            b, nextChar, currentPos, activeMatchSets, runnableMatches, prefilterActive);
      }
    }

    // Handle stragglers
    for (final MatchSet ms : activeMatchSets) {
      performMatches(b, ms.getMatches(), true);
    }

    activeMatchSets.clear();
  }

  /**
   * Optimized matching progress for ASCII characters.
   *
   * <p>Uses fast character classification and optimized buffer operations.
   */
  private void matcherProgressAscii(
      final Buffer b,
      final Character currentChar,
      final int currentPos,
      final Set<MatchSet> activeMatchSets,
      final RunnableMatchesHolder runnableMatches,
      final boolean prefilterActive) {

    checkNotNull(currentChar);
    checkArgument(currentPos >= 0);

    // Clear runnable matches
    ((RunnableMatchesHolderImpl) runnableMatches).clear();

    // Progress active match sets
    if (!activeMatchSets.isEmpty()) {
      final Set<MatchSet> toRemove = new HashSet<>();
      for (final MatchSet ms : activeMatchSets) {
        ms.progress(ns, currentChar, currentPos, runnableMatches);
        if (!ms.hasMatches()) {
          toRemove.add(ms);
        }
      }
      activeMatchSets.removeAll(toRemove);
    }

    // Check if we should start new matches at this position
    boolean shouldStartMatch = true;

    if (prefilterActive) {
      candidatePositionCursor =
          advanceCursor(candidatePositions, candidatePositionCursor, currentPos);
      shouldStartMatch =
          candidatePositionCursor < candidatePositions.length
              && candidatePositions[candidatePositionCursor] == currentPos;
    }

    if (shouldStartMatch) {
      final DFANode startNode = ns.getNextFromStartNode(currentChar);
      if (startNode != null) {
        Set<Regexp> candidateRegexps;

        if (prefilterActive) {
          mappedRegexCursor = advanceCursor(mappedRegexPositions, mappedRegexCursor, currentPos);
          if (mappedRegexCursor < mappedRegexPositions.length
              && mappedRegexPositions[mappedRegexCursor] == currentPos) {
            candidateRegexps = mappedRegexps.get(mappedRegexCursor);
          } else {
            candidateRegexps = startNode.getRegexpsThatCanStartWith(currentChar);
          }
        } else {
          candidateRegexps = startNode.getRegexpsThatCanStartWith(currentChar);
        }

        if (!candidateRegexps.isEmpty()) {
          final MatchSet ms =
              new MatchSetImpl(currentPos, startNode, currentChar, candidateRegexps);
          if (ms.hasMatches()) {
            activeMatchSets.add(ms);
          }
        }
      }
    }

    // Commit final matches
    final Iterator<MatchSet> msIterator = activeMatchSets.iterator();
    while (msIterator.hasNext()) {
      final MatchSet ms = msIterator.next();
      ms.finalCommit(runnableMatches);
      if (!ms.hasMatches()) {
        msIterator.remove();
      }
    }

    // Perform actions
    performMatches(b, runnableMatches.getMatches(), false);
  }

  /**
   * Matching progress for non-ASCII characters.
   *
   * <p>Falls back to standard processing for Unicode characters.
   */
  private void matcherProgressUnicode(
      final Buffer b,
      final Character currentChar,
      final int currentPos,
      final Set<MatchSet> activeMatchSets,
      final RunnableMatchesHolder runnableMatches,
      final boolean prefilterActive) {
    // For now, use the same logic as ASCII
    // Could be optimized differently for Unicode in the future
    matcherProgressAscii(
        b, currentChar, currentPos, activeMatchSets, runnableMatches, prefilterActive);
  }

  /**
   * Prepare prefilter candidates for a match invocation.
   *
   * @return true when prefilter candidates are prepared and can be used
   */
  private boolean preparePrefilterForMatch(final Buffer b) {
    if (!prefilterEnabled || prefilter == null) {
      resetPrefilterCandidates();
      return false;
    }

    final String fullText = collectBufferText(b);
    if (fullText == null) {
      resetPrefilterCandidates();
      return false;
    }

    runPrefilterScan(fullText);
    candidatePositionCursor = 0;
    mappedRegexCursor = 0;
    return true;
  }

  /** Collect buffer text for prefilter scanning without consuming the original buffer. */
  private String collectBufferText(final Buffer b) {
    try {
      return b.clone().getCurrentRestString();
    } catch (RuntimeException ex) {
      return null;
    }
  }

  /** Run prefilter scan to identify candidate positions. */
  private void runPrefilterScan(final String text) {
    if (text == null || prefilter == null) {
      resetPrefilterCandidates();
      return;
    }

    final List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan(text);
    final Set<Integer> candidatePositionSet = new HashSet<>();
    final Map<Integer, Set<Regexp>> mappedRegexpsByPosition = new HashMap<>();

    for (final AhoCorasickPrefilter.Candidate candidate : candidates) {
      final int startPos = candidate.startIndexForMatch();
      if (startPos >= 0) {
        candidatePositionSet.add(startPos);

        if (patternIdToRegexp != null) {
          final Regexp regexp = patternIdToRegexp.get(candidate.patternId());
          if (regexp != null) {
            mappedRegexpsByPosition.computeIfAbsent(startPos, k -> new HashSet<>()).add(regexp);
          }
        }
      }
    }

    candidatePositions = candidatePositionSet.stream().mapToInt(Integer::intValue).toArray();
    Arrays.sort(candidatePositions);

    if (mappedRegexpsByPosition.isEmpty()) {
      mappedRegexPositions = EMPTY_INT_ARRAY;
      mappedRegexps = Collections.emptyList();
      return;
    }

    mappedRegexPositions = mappedRegexpsByPosition.keySet().stream().mapToInt(Integer::intValue).toArray();
    Arrays.sort(mappedRegexPositions);

    final List<Set<Regexp>> orderedRegexps = new ArrayList<>(mappedRegexPositions.length);
    for (final int position : mappedRegexPositions) {
      orderedRegexps.add(mappedRegexpsByPosition.get(position));
    }
    mappedRegexps = orderedRegexps;
  }

  /** Perform match actions. */
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

  /** Perform a single match action. */
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

  private void resetPrefilterCandidates() {
    candidatePositions = EMPTY_INT_ARRAY;
    mappedRegexPositions = EMPTY_INT_ARRAY;
    mappedRegexps = Collections.emptyList();
    candidatePositionCursor = 0;
    mappedRegexCursor = 0;
  }

  private static int advanceCursor(final int[] positions, int cursor, final int currentPos) {
    while (cursor < positions.length && positions[cursor] < currentPos) {
      cursor++;
    }
    return cursor;
  }
}
