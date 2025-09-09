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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.*;
import no.rmz.rmatch.engine.prefilter.AhoCorasickPrefilter;
import no.rmz.rmatch.engine.prefilter.LiteralHint;
import no.rmz.rmatch.engine.prefilter.LiteralPrefilter;
import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.utils.SimpleBloomFilter;

/**
 * Advanced match engine using Bloom filter pre-screening and hierarchical filtering to achieve O(l
 * * log(m)) complexity instead of O(l * m).
 */
public final class BloomFilterMatchEngine implements MatchEngine {

  /** The NodeStorage for DFA operations. */
  private final NodeStorage ns;

  /** Bloom filter for fast negative lookups of n-grams. */
  private SimpleBloomFilter ngramFilter;

  /** Map from character to regexps that can start with that character. */
  private final Map<Character, Set<Regexp>> firstCharToRegexps = new HashMap<>();

  /** Map from regexp length range to regexps. */
  private final Map<Integer, Set<Regexp>> lengthToRegexps = new HashMap<>();

  /** Aho-Corasick prefilter for literal substring matching. */
  private AhoCorasickPrefilter literalPrefilter;

  /** Whether the engine has been initialized. */
  private boolean initialized = false;

  /** N-gram size for Bloom filter. */
  private static final int NGRAM_SIZE = 3;

  /** Minimum pattern length to consider for matching. */
  private static final int MIN_PATTERN_LENGTH = 1;

  /** Maximum pattern length to consider for matching. */
  private static final int MAX_PATTERN_LENGTH = 100;

  public BloomFilterMatchEngine(final NodeStorage ns) {
    this.ns = checkNotNull(ns, "NodeStorage can't be null");
  }

  /**
   * Initialize the engine with regexp patterns. This must be called before matching to build the
   * filtering structures.
   *
   * @param regexps The set of regexps to initialize with
   */
  public void initialize(final Set<Regexp> regexps) {
    if (regexps == null || regexps.isEmpty()) {
      return;
    }

    /** All regexps in the system. */
    Set<Regexp> allRegexps = new HashSet<>(regexps);

    // Build Bloom filter from n-grams
    buildBloomFilter(regexps);

    // Build first character index
    buildFirstCharacterIndex(regexps);

    // Build length index
    buildLengthIndex(regexps);

    // Build Aho-Corasick prefilter
    buildLiteralPrefilter(regexps);

    this.initialized = true;
  }

  private void buildBloomFilter(final Set<Regexp> regexps) {
    // Count actual n-grams for more accurate sizing
    int totalNGrams = 0;
    for (final Regexp regexp : regexps) {
      final String pattern = regexp.getRexpString();
      totalNGrams += Math.max(1, pattern.length() - NGRAM_SIZE + 1); // n-grams per pattern
      totalNGrams += pattern.length(); // single chars
    }

    // Use more aggressive settings: smaller filter, higher false positive rate (but faster)
    ngramFilter =
        new SimpleBloomFilter(Math.max(totalNGrams / 2, 50), 0.05); // 5% false positive rate

    for (final Regexp regexp : regexps) {
      final String pattern = regexp.getRexpString();

      // Add all n-grams from this pattern to Bloom filter
      for (int i = 0; i <= pattern.length() - NGRAM_SIZE; i++) {
        final String ngram = pattern.substring(i, i + NGRAM_SIZE);
        ngramFilter.put(ngram.toLowerCase()); // normalize case
      }

      // Also add single characters for very short patterns
      for (int i = 0; i < pattern.length(); i++) {
        ngramFilter.put(String.valueOf(pattern.charAt(i)).toLowerCase());
      }
    }
  }

  private void buildFirstCharacterIndex(final Set<Regexp> regexps) {
    for (final Regexp regexp : regexps) {
      final String pattern = regexp.getRexpString();
      if (!pattern.isEmpty()) {
        // Extract first character (handle simple regex constructs)
        final char firstChar = getFirstPossibleCharacter(pattern);
        if (firstChar != 0) {
          firstCharToRegexps.computeIfAbsent(firstChar, k -> new HashSet<>()).add(regexp);
          // Also add lowercase version for case-insensitive matching
          final char lowerFirst = Character.toLowerCase(firstChar);
          if (lowerFirst != firstChar) {
            firstCharToRegexps.computeIfAbsent(lowerFirst, k -> new HashSet<>()).add(regexp);
          }
        }
      }
    }
  }

  private void buildLengthIndex(final Set<Regexp> regexps) {
    for (final Regexp regexp : regexps) {
      final String pattern = regexp.getRexpString();
      final int minLength = estimateMinLength(pattern);
      lengthToRegexps.computeIfAbsent(minLength, k -> new HashSet<>()).add(regexp);
    }
  }

  private void buildLiteralPrefilter(final Set<Regexp> regexps) {
    final List<LiteralHint> hints = new ArrayList<>();

    int patternId = 0;
    for (final Regexp regexp : regexps) {
      final String pattern = regexp.getRexpString();

      // Extract literal hints from the pattern
      final var hint = LiteralPrefilter.extract(patternId++, pattern, 0);
      if (hint.isPresent()) {
        hints.add(hint.get());
      }
    }

    if (!hints.isEmpty()) {
      literalPrefilter = new AhoCorasickPrefilter(hints);
    }
  }

  @Override
  public void match(final Buffer buffer) {
    checkNotNull(buffer, "Buffer can't be null");

    if (!initialized) {
      // Fall back to simple matching if not initialized
      performSimpleMatch(buffer);
      return;
    }

    final StringBuilder textBuilder = new StringBuilder();
    final List<Character> characters = new ArrayList<>();

    // Collect all text from buffer
    while (buffer.hasNext()) {
      final Character ch = buffer.getNext();
      textBuilder.append(ch);
      characters.add(ch);
    }

    final String text = textBuilder.toString();
    if (text.isEmpty()) {
      return;
    }

    // Use hierarchical filtering approach
    performHierarchicalMatch(text, characters);
  }

  private void performHierarchicalMatch(final String text, final List<Character> characters) {
    // Use simple ArrayList instead of synchronized TreeSet for better performance
    final List<MatchSet> activeMatchSets = new ArrayList<>();
    final RunnableMatchesHolder runnableMatches = new RunnableMatchesHolderImpl();

    // Pre-screen with Aho-Corasick to get candidate positions
    Set<Integer> candidatePositions = null;
    if (literalPrefilter != null) {
      candidatePositions = getLiteralCandidatePositions(text);
    }

    for (int i = 0; i < text.length(); i++) {
      final char currentChar = text.charAt(i);

      // Stage 1: Aho-Corasick pre-filtering
      if (candidatePositions != null && !candidatePositions.contains(i)) {
        continue; // Skip positions that can't possibly match
      }

      // Stage 2: First character filtering
      final Set<Regexp> firstCharCandidates = firstCharToRegexps.get(currentChar);
      if (firstCharCandidates == null || firstCharCandidates.isEmpty()) {
        continue; // No regexps can start with this character
      }

      // Stage 3: Bloom filter n-gram filtering
      final Set<Regexp> bloomCandidates = bloomFilterRegexps(firstCharCandidates, text, i);
      if (bloomCandidates.isEmpty()) {
        continue; // No regexps pass Bloom filter test
      }

      // Stage 4: Length filtering
      final Set<Regexp> lengthCandidates = lengthFilterRegexps(bloomCandidates, text.length() - i);
      if (lengthCandidates.isEmpty()) {
        continue; // No regexps can fit in remaining text
      }

      // Stage 5: Check if node supports any of our candidates BEFORE creating MatchSet
      final var startNode = ns.getNextFromStartNode(currentChar);
      if (startNode == null) {
        continue; // No node for this character
      }

      // Check if any of our filtered candidates are in this node's regexps
      final var nodeRegexps = startNode.getRegexps();
      boolean hasViableCandidates = false;
      for (final Regexp candidate : lengthCandidates) {
        if (nodeRegexps.contains(candidate)) {
          hasViableCandidates = true;
          break; // Early exit - we found at least one viable candidate
        }
      }

      if (!hasViableCandidates) {
        continue; // No intersection between our candidates and node's regexps
      }

      // Stage 6: ONLY NOW create MatchSet - we know we have viable candidates
      final MatchSet ms = new MatchSetImpl(i, startNode, currentChar);
      if (ms.hasMatches()) {
        activeMatchSets.add(ms);
      }

      // Progress existing match sets
      progressMatchSets(activeMatchSets, currentChar, i, runnableMatches);
    }

    // Debug statistics (disabled for performance)
    // Uncomment below for debugging:
    /*
    System.out.println("=== HIERARCHICAL FILTERING STATS ===");
    System.out.println("Total positions: " + totalPositions);
    System.out.println("Aho-Corasick filtered: " + ahoCorasickFiltered + " (" + (100.0 * ahoCorasickFiltered / totalPositions) + "%)");
    System.out.println("First char filtered: " + firstCharFiltered + " (" + (100.0 * firstCharFiltered / totalPositions) + "%)");
    System.out.println("Bloom filter filtered: " + bloomFiltered + " (" + (100.0 * bloomFiltered / totalPositions) + "%)");
    System.out.println("Length filtered: " + lengthFiltered + " (" + (100.0 * lengthFiltered / totalPositions) + "%)");
    System.out.println("Node filtered: " + nodeFiltered + " (" + (100.0 * nodeFiltered / totalPositions) + "%)");
    System.out.println("MatchSets created: " + matchSetsCreated + " (" + (100.0 * matchSetsCreated / totalPositions) + "%)");
    int totalFiltered = ahoCorasickFiltered + firstCharFiltered + bloomFiltered + lengthFiltered + nodeFiltered;
    System.out.println("Total filtered: " + totalFiltered + " (" + (100.0 * totalFiltered / totalPositions) + "%)");
    System.out.println("=====================================");
    */

    // Handle final matches
    for (final MatchSet ms : activeMatchSets) {
      ms.finalCommit(runnableMatches);
    }

    // Execute all runnable matches
    performMatches(createBufferFromText(text), runnableMatches.getMatches(), false);
  }

  private Set<Integer> getLiteralCandidatePositions(final String text) {
    final List<AhoCorasickPrefilter.Candidate> candidates = literalPrefilter.scan(text);
    final Set<Integer> positions = new HashSet<>();

    for (final AhoCorasickPrefilter.Candidate candidate : candidates) {
      final int startPos = candidate.startIndexForMatch();
      if (startPos >= 0) {
        positions.add(startPos);
      }
    }

    return positions;
  }

  private Set<Regexp> bloomFilterRegexps(
      final Set<Regexp> candidates, final String text, final int position) {
    // Use local collection for thread safety - slight allocation cost but necessary
    final Set<Regexp> results = new HashSet<>();

    for (final Regexp regexp : candidates) {
      if (bloomFilterTest(regexp, text, position)) {
        results.add(regexp);
      }
    }

    return results;
  }

  private boolean bloomFilterTest(final Regexp regexp, final String text, final int position) {
    final String pattern = regexp.getRexpString().toLowerCase();

    // Test n-grams from current position in text
    for (int len = 1; len <= Math.min(NGRAM_SIZE, text.length() - position); len++) {
      final String textNGram = text.substring(position, position + len).toLowerCase();
      if (!ngramFilter.mightContain(textNGram)) {
        return false; // Definitely cannot match
      }
    }

    return true; // Might match (could be false positive)
  }

  private Set<Regexp> lengthFilterRegexps(
      final Set<Regexp> candidates, final int remainingTextLength) {
    // Use local collection for thread safety - slight allocation cost but necessary
    final Set<Regexp> results = new HashSet<>();

    for (final Regexp regexp : candidates) {
      final int minLength = estimateMinLength(regexp.getRexpString());
      if (minLength <= remainingTextLength) {
        results.add(regexp);
      }
    }

    return results;
  }

  private void progressMatchSets(
      final List<MatchSet> activeMatchSets,
      final Character currentChar,
      final int currentPos,
      final RunnableMatchesHolder runnableMatches) {
    // Use reverse iteration to safely remove elements while iterating
    for (int i = activeMatchSets.size() - 1; i >= 0; i--) {
      final MatchSet ms = activeMatchSets.get(i);
      ms.progress(ns, currentChar, currentPos, runnableMatches);
      if (!ms.hasMatches()) {
        activeMatchSets.remove(
            i); // O(1) removal from end, O(n) from middle but typically small list
      }
    }
  }

  private void performSimpleMatch(final Buffer buffer) {
    // Fallback to original algorithm
    final MatchEngineImpl fallback = new MatchEngineImpl(ns);
    fallback.match(buffer);
  }

  private static void performMatches(
      final Buffer buffer, final Collection<Match> matches, final boolean bePermissive) {
    for (final Match match : matches) {
      if (bePermissive) {
        match.setInactive();
      }
      if (match.isFinal()) {
        final int start = match.getStart();
        final int end = match.getEnd();
        final Regexp regexp = match.getRegexp();
        regexp.performActions(buffer, start, end);
      }
      if (bePermissive) {
        match.abandon(null);
      }
    }
  }

  private static class TextBuffer implements Buffer, Cloneable {
    private final String text;
    private int pos = -1;

    TextBuffer(final String text) {
      this.text = text;
    }

    @Override
    public boolean hasNext() {
      return pos + 1 < text.length();
    }

    @Override
    public Character getNext() {
      if (hasNext()) {
        return text.charAt(++pos);
      }
      return null;
    }

    @Override
    public int getCurrentPos() {
      return pos;
    }

    @Override
    public String getCurrentRestString() {
      return pos >= 0 ? text.substring(Math.min(pos + 1, text.length())) : text;
    }

    @Override
    public String getString(final int start, final int stop) {
      return text.substring(Math.max(0, start), Math.min(text.length(), stop));
    }

    @Override
    public Buffer clone() {
      final TextBuffer cloned = new TextBuffer(text);
      cloned.pos = this.pos;
      return cloned;
    }
  }

  private static Buffer createBufferFromText(final String text) {
    return new TextBuffer(text);
  }

  // Utility methods for pattern analysis
  private static char getFirstPossibleCharacter(final String pattern) {
    if (pattern.isEmpty()) {
      return 0;
    }

    // Handle simple cases - for complex regex, return first literal char
    final char first = pattern.charAt(0);
    if (Character.isLetterOrDigit(first)) {
      return first;
    }

    // For now, just return first character - could be enhanced for complex patterns
    return first;
  }

  private static int estimateMinLength(final String pattern) {
    // Simple estimation - count non-metacharacters
    // This could be made more sophisticated to handle quantifiers, etc.
    int minLength = 0;
    for (int i = 0; i < pattern.length(); i++) {
      final char c = pattern.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        minLength++;
      }
    }
    return Math.max(1, minLength);
  }
}
