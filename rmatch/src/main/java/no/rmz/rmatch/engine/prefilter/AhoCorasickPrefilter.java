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
package no.rmz.rmatch.engine.prefilter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

/**
 * Aho-Corasick based prefilter for fast literal substring matching.
 *
 * <p>This class builds an Aho-Corasick automaton from literal hints extracted from regex patterns.
 * It can then scan input text efficiently to find all occurrences of the literals, providing
 * candidate positions where regex matching should be attempted.
 *
 * <p>Earlier rmatch versions delegated this job to Robert Bor's excellent {@code
 * org.ahocorasick:ahocorasick} library. We now keep a small internal implementation because Java's
 * module system cannot treat that library as a stable explicit module. The algorithmic debt and
 * credit remain exactly where they belong: this is the classic Aho-Corasick construction, adapted
 * narrowly to rmatch's literal-prefilter needs because time does not stand still.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * List<LiteralHint> hints = Arrays.asList(
 *     new LiteralHint(1, "foo", 0, true, false, 0),
 *     new LiteralHint(2, "bar", 5, false, false, 0)
 * );
 * AhoCorasickPrefilter prefilter = new AhoCorasickPrefilter(hints);
 * List<Candidate> candidates = prefilter.scan("foo and bar test");
 * }</pre>
 */
public final class AhoCorasickPrefilter {

  /**
   * Represents a candidate position where regex matching should be attempted.
   *
   * @param patternId The pattern ID that this candidate corresponds to.
   * @param endIndexExclusive End position of literal in the text (exclusive).
   * @param literalLength Length of the matched literal.
   * @param literalOffsetInMatch Offset of the literal within the expected match.
   */
  public record Candidate(
      int patternId, int endIndexExclusive, int literalLength, int literalOffsetInMatch) {
    /**
     * Creates a new candidate.
     *
     * @param patternId the pattern ID
     * @param endIndexExclusive end position of literal in text (exclusive)
     * @param literalLength length of the matched literal
     * @param literalOffsetInMatch offset of literal within the expected match
     */
    public Candidate {}

    /**
     * Calculates the start index for regex matching.
     *
     * <p>Places the regex start position so that the literal aligns at the expected offset within
     * the match.
     *
     * @return the index where regex matching should start
     */
    public int startIndexForMatch() {
      // Place regex start so that literal aligns at offset:
      // end = idx+len; literal starts at end - literalLength
      final int literalStartInText = endIndexExclusive - literalLength;
      return literalStartInText - literalOffsetInMatch;
    }

    @Override
    public String toString() {
      return "Candidate{"
          + "patternId="
          + patternId
          + ", endIndexExclusive="
          + endIndexExclusive
          + ", literalLength="
          + literalLength
          + ", literalOffsetInMatch="
          + literalOffsetInMatch
          + '}';
    }
  }

  /** The Aho-Corasick trie for efficient string matching. */
  private final List<Node> nodes = new ArrayList<>();

  /** Map from literal string to the pattern hints that contain it. */
  private final Map<String, Bucket> buckets = new HashMap<>();

  private record Bucket(LiteralHint[] hints, int literalLength) {}

  /**
   * Constructs an Aho-Corasick prefilter from a collection of literal hints.
   *
   * @param hints the literal hints to build the automaton from
   */
  public AhoCorasickPrefilter(final Collection<LiteralHint> hints) {
    nodes.add(new Node());
    final Map<String, List<LiteralHint>> tmpBuckets = new HashMap<>();
    // If any hint is case-insensitive, we'll add both cased variants at build time.
    for (final LiteralHint hint : hints) {
      for (final String key : keyedLiterals(hint)) {
        tmpBuckets.computeIfAbsent(key, k -> new ArrayList<>()).add(hint);
      }
    }
    for (final Map.Entry<String, List<LiteralHint>> entry : tmpBuckets.entrySet()) {
      final String key = entry.getKey();
      final Bucket bucket = new Bucket(entry.getValue().toArray(LiteralHint[]::new), key.length());
      buckets.put(key, bucket);
      addKeyword(key, bucket);
    }
    buildFailureLinks();
  }

  /**
   * Generates the keyed literals for a hint, handling case insensitivity.
   *
   * @param hint the literal hint
   * @return list of literal variants to add to the trie
   */
  private static List<String> keyedLiterals(final LiteralHint hint) {
    if (!hint.caseInsensitive()) {
      return List.of(hint.literal());
    }
    // naive CI expansion: add lower and upper; Java case rules are locale-sensitive—use ROOT if
    // you normalize upstream
    final String lower = hint.literal().toLowerCase(Locale.ROOT);
    final String upper = hint.literal().toUpperCase(Locale.ROOT);
    if (lower.equals(upper)) {
      return List.of(lower);
    }
    if (lower.length() == upper.length()) {
      return List.of(lower, upper);
    }
    // fallback—just use lower
    return List.of(lower);
  }

  /**
   * Scans the input text for literal matches and returns candidate positions.
   *
   * @param text the text to scan
   * @return list of candidate positions where regex matching should be attempted
   */
  public List<Candidate> scan(final CharSequence text) {
    final List<Candidate> out = new ArrayList<>();
    int state = 0;
    for (int i = 0; i < text.length(); i++) {
      final char ch = text.charAt(i);
      while (state != 0 && !nodes.get(state).transitions.containsKey(ch)) {
        state = nodes.get(state).failure;
      }
      final Integer next = nodes.get(state).transitions.get(ch);
      state = next == null ? 0 : next;
      final Node node = nodes.get(state);
      if (!node.outputs.isEmpty()) {
        final int endIdxExclusive = i + 1;
        for (final Bucket bucket : node.outputs) {
          for (final LiteralHint hint : bucket.hints) {
            out.add(
                new Candidate(
                    hint.patternId(),
                    endIdxExclusive,
                    bucket.literalLength,
                    hint.literalOffsetInMatch()));
          }
        }
      }
    }
    return out;
  }

  private void addKeyword(final String keyword, final Bucket bucket) {
    int state = 0;
    for (int i = 0; i < keyword.length(); i++) {
      final char ch = keyword.charAt(i);
      final Node node = nodes.get(state);
      final Integer existing = node.transitions.get(ch);
      if (existing != null) {
        state = existing;
      } else {
        final int created = nodes.size();
        nodes.add(new Node());
        node.transitions.put(ch, created);
        state = created;
      }
    }
    nodes.get(state).outputs.add(bucket);
  }

  private void buildFailureLinks() {
    final Queue<Integer> queue = new ArrayDeque<>();
    final Node root = nodes.get(0);
    for (final int child : root.transitions.values()) {
      nodes.get(child).failure = 0;
      queue.add(child);
    }

    while (!queue.isEmpty()) {
      final int state = queue.remove();
      final Node stateNode = nodes.get(state);
      for (final Map.Entry<Character, Integer> transition : stateNode.transitions.entrySet()) {
        final char ch = transition.getKey();
        final int target = transition.getValue();

        int fallback = stateNode.failure;
        while (fallback != 0 && !nodes.get(fallback).transitions.containsKey(ch)) {
          fallback = nodes.get(fallback).failure;
        }
        final Integer fallbackTarget = nodes.get(fallback).transitions.get(ch);
        nodes.get(target).failure = fallbackTarget == null ? 0 : fallbackTarget;
        nodes.get(target).outputs.addAll(nodes.get(nodes.get(target).failure).outputs);
        queue.add(target);
      }
    }
  }

  private static final class Node {
    private final Map<Character, Integer> transitions = new HashMap<>();
    private final List<Bucket> outputs = new ArrayList<>();
    private int failure;
  }
}
