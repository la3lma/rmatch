/**
 * Copyright 2026. Bjørn Remseth (rmz@rmz.no).
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
package no.rmz.rmatch.compiler;

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;

/** Builder for an NDFA fragment that accepts one character from a set of literals or ranges. */
public final class CharSetBuilder {

  /** The set of characters that this builder will look for. */
  private final StringBuilder charSetStringBuilder = new StringBuilder();

  /** A collection of char-ranges that this matcher will look for. */
  private final Set<CharRange> charRanges = new TreeSet<>();

  /**
   * True iff this CharSet should be treated as an "inverted" CharSet, i.e. one that will match
   * everything -except- the characters in the char set represented by the CharSet.
   */
  private boolean isInverted;

  /** The regexp that contains this CharSet. */
  private final Regexp regexp;

  /**
   * Create a new CharSet builder for a regexp.
   *
   * @param regexp The regexp that the char set is a part of.
   */
  public CharSetBuilder(final Regexp regexp) {
    this.regexp = checkNotNull(regexp);
    isInverted = false;
  }

  /**
   * Build a CompiledFragment that represents the CharSet.
   *
   * @return the compilation result.
   */
  public CompiledFragment build() {
    final CompiledFragment result = new CompiledFragment(regexp);
    final NDFANode arrival = result.getArrivalNode();
    final NDFANode endNode = result.getEndingNode();

    if (isInverted) {
      // KB-4: a negated set must be a REAL character class matching exactly the complement.
      // The previous construction ("match any char, but also route set members to a FailNode
      // and let the engine's failing machinery kill the match afterwards") was unsound: one
      // failing NDFA path killed the whole regexp's match even when other, legal paths
      // survived (".+[^a]?" lost matches whose .+ path was alive), and the failing flag was
      // never honored on the match's first node at all (so [^a] happily matched 'a').
      for (final CharRange range : complementRanges()) {
        arrival.addEpsilonEdge(new CharRangeNode(range, regexp, endNode));
      }
      return result;
    }

    // To match, make an NDFA node per character in the set
    // and pass through to the end node if matching one of the chars.
    final String str = charSetStringBuilder.toString();
    for (int i = str.length() - 1; i >= 0; i--) {
      final char myChar = str.charAt(i);
      final NDFANode node = new CharNode(endNode, myChar, regexp);
      arrival.addEpsilonEdge(node);
    }

    // Add more opportunities to match by  NDFA node per range in the set
    // and pass through to the end node if matching one of the ranges.
    for (final CharRange range : charRanges) {
      arrival.addEpsilonEdge(new CharRangeNode(range, regexp, endNode));
    }

    return result;
  }

  /**
   * Compute the complement of this set's characters and ranges over the full char domain, as a
   * minimal list of inclusive ranges.
   *
   * @return ranges covering exactly the characters NOT in this set.
   */
  private List<CharRange> complementRanges() {
    // Collect all member intervals (inclusive char values).
    final List<int[]> intervals = new ArrayList<>();
    final String str = charSetStringBuilder.toString();
    for (int i = 0; i < str.length(); i++) {
      intervals.add(new int[] {str.charAt(i), str.charAt(i)});
    }
    for (final CharRange r : charRanges) {
      intervals.add(new int[] {r.start(), r.end()});
    }
    intervals.sort((x, y) -> Integer.compare(x[0], y[0]));

    // Sweep the sorted intervals, emitting the gaps between them.
    final List<CharRange> complement = new ArrayList<>();
    int next = 0; // smallest char value not yet accounted for
    for (final int[] iv : intervals) {
      if (iv[0] > next) {
        complement.add(new CharRange((char) next, (char) (iv[0] - 1)));
      }
      next = Math.max(next, iv[1] + 1);
      if (next > Character.MAX_VALUE) {
        return complement;
      }
    }
    complement.add(new CharRange((char) next, Character.MAX_VALUE));
    return complement;
  }

  /**
   * Add a bunch of characters to the set we're matching.
   *
   * @param cs characters in a string ;)
   */
  void addChars(final String cs) {
    charSetStringBuilder.append(cs);
  }

  /** Treat this set of characters inverted. */
  void invert() {
    isInverted = true;
  }

  /**
   * Add a range of characters.
   *
   * @param startOfRange The first character in the range.
   * @param endOfRange The last character in the range.
   */
  void addRange(final char startOfRange, final char endOfRange) {
    charRanges.add(new CharRange(startOfRange, endOfRange));
  }
}
