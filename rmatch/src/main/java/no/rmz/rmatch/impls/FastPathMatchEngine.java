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

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import java.util.*;
import no.rmz.rmatch.Buffer;
import no.rmz.rmatch.engine.fastpath.AsciiOptimizer;
import no.rmz.rmatch.engine.fastpath.StateSetBuffers;
import no.rmz.rmatch.engine.prefilter.AhoCorasickPrefilter;
import no.rmz.rmatch.engine.prefilter.LiteralHint;
import no.rmz.rmatch.engine.prefilter.LiteralPrefilter;
import no.rmz.rmatch.engine.prefilter.PrefilterSafety;
import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.utils.RegexStringBuffer;

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
final class FastPathMatchEngine implements MatchEngine {
  private static final int[] EMPTY_INT_ARRAY = new int[0];

  /** Number of characters covered by the two-character start-filter cache. */
  private static final int ASCII_LIMIT = 128;

  /**
   * Cache of start candidates keyed by the first two characters of a potential match. A regexp can
   * produce a match starting with characters (c1, c2) only if it is still alive in the DFA after
   * consuming both, or if it has a complete length-1 match on c1 alone (c1 reaches a terminal
   * state). Both are exact DFA properties, so this filter never changes match semantics — it only
   * avoids creating speculative matches that are guaranteed to die on the second character.
   *
   * <p>Rows are allocated lazily per first character; entries lazily per second character.
   * Invalidated in configurePrefilter, which runs whenever the pattern set changes.
   */
  @SuppressWarnings("unchecked")
  private final Set<Regexp>[][] twoCharStartCache = new Set[ASCII_LIMIT][];

  /** The NodeStorage instance. */
  private final NodeStorage ns;

  /** Optional AhoCorasick prefilter (null if disabled). */
  private AhoCorasickPrefilter prefilter;

  /** Map from pattern IDs to Regexp objects. */
  private Map<Integer, Regexp> patternIdToRegexp;

  /** Whether prefiltering is enabled. */
  private final boolean prefilterEnabled;

  /**
   * Default minimum pattern count threshold for prefilter activation. Default 5000 is optimal based
   * on testing.
   */
  private static final int DEFAULT_PREFILTER_ACTIVATION_THRESHOLD = 5000;

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
    // The pattern set changed: cached two-character start candidates are stale.
    Arrays.fill(twoCharStartCache, null);

    if (!prefilterEnabled
        || patterns.isEmpty()
        || patterns.size() < prefilterActivationThreshold()) {
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
      hint.filter(extracted -> PrefilterSafety.isSafeHint(regex, extracted)).ifPresent(hints::add);
    }

    if (!hints.isEmpty() && hints.size() == patterns.size()) {
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
    final boolean contextAssertions = ns.hasContextAssertions();

    // Get a reusable holder for runnable matches
    final RunnableMatchesHolder runnableMatches = new RunnableMatchesHolderImpl();

    if (contextAssertions) {
      Character previousChar = null;
      for (long pos = 0; b.hasCharAt(pos); pos++) {
        final Character nextChar = b.charAt(pos);
        final int currentPos = (int) pos;
        final MatchContext context = contextForPosition(b, pos, previousChar, nextChar);
        if (AsciiOptimizer.isAscii(nextChar)) {
          matcherProgressAscii(
              b, nextChar, currentPos, activeMatchSets, runnableMatches, prefilterActive, context);
        } else {
          matcherProgressUnicode(
              b, nextChar, currentPos, activeMatchSets, runnableMatches, prefilterActive, context);
        }
        previousChar = nextChar;
      }
    } else {
      for (long pos = 0; b.hasCharAt(pos); pos++) {
        final Character nextChar = b.charAt(pos);
        final int currentPos = (int) pos;
        if (AsciiOptimizer.isAscii(nextChar)) {
          matcherProgressAscii(
              b,
              nextChar,
              currentPos,
              activeMatchSets,
              runnableMatches,
              prefilterActive,
              MatchContext.NONE);
        } else {
          matcherProgressUnicode(
              b,
              nextChar,
              currentPos,
              activeMatchSets,
              runnableMatches,
              prefilterActive,
              MatchContext.NONE);
        }
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
      final boolean prefilterActive,
      final MatchContext context) {

    // Hot path: called once per input character. Internal invariants (non-null char,
    // non-negative position) are guaranteed by the match() loop, so no precondition
    // checks here.

    // Clear runnable matches
    ((RunnableMatchesHolderImpl) runnableMatches).clear();

    // Progress active match sets
    if (!activeMatchSets.isEmpty()) {
      final Set<MatchSet> toRemove = new HashSet<>();
      for (final MatchSet ms : activeMatchSets) {
        if (context == MatchContext.NONE) {
          ms.progress(ns, currentChar, currentPos, runnableMatches);
        } else {
          ms.progress(ns, currentChar, currentPos, runnableMatches, context);
        }
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
      final DFANode startNode =
          context == MatchContext.NONE
              ? ns.getNextFromStartNode(currentChar)
              : ns.getNextFromStartNode(currentChar, context);
      if (startNode != null) {
        Set<Regexp> candidateRegexps;

        if (prefilterActive) {
          mappedRegexCursor = advanceCursor(mappedRegexPositions, mappedRegexCursor, currentPos);
          if (mappedRegexCursor < mappedRegexPositions.length
              && mappedRegexPositions[mappedRegexCursor] == currentPos) {
            candidateRegexps = mappedRegexps.get(mappedRegexCursor);
          } else {
            candidateRegexps = startCandidates(b, currentPos, currentChar, startNode);
          }
        } else {
          candidateRegexps = startCandidates(b, currentPos, currentChar, startNode, context);
        }

        if (!candidateRegexps.isEmpty()) {
          final MatchSet ms =
              new MatchSetImpl(currentPos, startNode, currentChar, candidateRegexps, context);
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
      final boolean prefilterActive,
      final MatchContext context) {
    // For now, use the same logic as ASCII
    // Could be optimized differently for Unicode in the future
    matcherProgressAscii(
        b, currentChar, currentPos, activeMatchSets, runnableMatches, prefilterActive, context);
  }

  /**
   * Prepare prefilter candidates for a match invocation.
   *
   * @return true when prefilter candidates are prepared and can be used
   */
  private boolean preparePrefilterForMatch(final Buffer b) {
    if (ns.hasContextAssertions() || !prefilterEnabled || prefilter == null) {
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

  /** Collect buffer text for prefilter scanning. Buffers are content-only and unaffected. */
  private String collectBufferText(final Buffer b) {
    if (b instanceof RegexStringBuffer rsb) {
      return rsb.getString(0, rsb.getLength());
    }
    final StringBuilder text = new StringBuilder();
    for (long i = 0; b.hasCharAt(i); i++) {
      text.append(b.charAt(i));
    }
    return text.toString();
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

    mappedRegexPositions =
        mappedRegexpsByPosition.keySet().stream().mapToInt(Integer::intValue).toArray();
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

  /**
   * Compute the set of regexps worth starting a match for at the current position, using up to two
   * characters of context read directly from the buffer.
   *
   * <p>Falls back to the one-character filter at end of input or for non-ASCII characters — so this
   * is purely an optimization layer, never a semantic change.
   */
  private Set<Regexp> startCandidates(
      final Buffer b, final int currentPos, final char c1, final DFANode startNode) {
    return startCandidates(b, currentPos, c1, startNode, MatchContext.NONE);
  }

  private Set<Regexp> startCandidates(
      final Buffer b,
      final int currentPos,
      final char c1,
      final DFANode startNode,
      final MatchContext context) {
    if (context != MatchContext.NONE) {
      return startNode.getRegexpsThatCanStartWith(c1, context);
    }
    final Set<Regexp> oneChar = startNode.getRegexpsThatCanStartWith(c1);
    if (oneChar.isEmpty() || c1 >= ASCII_LIMIT) {
      return oneChar;
    }
    if (!b.hasCharAt(currentPos + 1L)) {
      // Last character of the buffer: only length-1 matches are possible, but the
      // one-character set is a safe (and tiny-cost) over-approximation here.
      return oneChar;
    }
    final char c2 = b.charAt(currentPos + 1L);
    if (c2 >= ASCII_LIMIT) {
      return oneChar;
    }

    Set<Regexp>[] row = twoCharStartCache[c1];
    if (row == null) {
      @SuppressWarnings("unchecked")
      final Set<Regexp>[] newRow = new Set[ASCII_LIMIT];
      row = newRow;
      twoCharStartCache[c1] = row;
    }
    Set<Regexp> cached = row[c2];
    if (cached == null) {
      cached = computeTwoCharCandidates(oneChar, startNode, c2);
      row[c2] = cached;
    }
    return cached;
  }

  /**
   * Exact two-character candidate set: regexps from the one-character set that either survive the
   * DFA transition on the second character, or already have a complete length-1 match after the
   * first character (terminal at the start node's successor).
   */
  private Set<Regexp> computeTwoCharCandidates(
      final Set<Regexp> oneChar, final DFANode startNode, final char c2) {
    if (!(startNode instanceof DFANodeImpl startNodeImpl)) {
      // Unknown DFA node implementation: no safe way to read terminal sets cheaply.
      return oneChar;
    }
    final Set<Regexp> terminalAfterOneChar = startNodeImpl.getTerminalRegexpsCached();
    final DFANode afterTwoChars = startNode.getNext(c2, ns);
    final Set<Regexp> aliveAfterTwoChars =
        afterTwoChars == null ? Collections.emptySet() : afterTwoChars.getRegexps();

    final Set<Regexp> result = new HashSet<>();
    for (final Regexp r : oneChar) {
      if (aliveAfterTwoChars.contains(r) || terminalAfterOneChar.contains(r)) {
        result.add(r);
      }
    }
    if (result.size() == oneChar.size()) {
      // Nothing was filtered; share the existing set instead of a copy.
      return oneChar;
    }
    return Collections.unmodifiableSet(result);
  }

  private static MatchContext contextForPosition(
      final Buffer b,
      final long currentPos,
      final Character previousChar,
      final Character currentChar) {
    final Character nextChar = b.hasCharAt(currentPos + 1) ? b.charAt(currentPos + 1) : null;
    return MatchContext.forPosition((int) currentPos, previousChar, currentChar, nextChar);
  }

  private static int prefilterActivationThreshold() {
    try {
      return Math.max(
          1,
          Integer.parseInt(
              System.getProperty(
                  "rmatch.prefilter.threshold",
                  String.valueOf(DEFAULT_PREFILTER_ACTIVATION_THRESHOLD))));
    } catch (NumberFormatException ignored) {
      return DEFAULT_PREFILTER_ACTIVATION_THRESHOLD;
    }
  }
}
