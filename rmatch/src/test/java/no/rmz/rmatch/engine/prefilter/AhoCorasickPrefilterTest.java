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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the AhoCorasickPrefilter class.
 *
 * <p>Verifies correct Aho-Corasick automaton construction and scanning, including handling of
 * multiple patterns, case-insensitive matching, and candidate position calculation.
 */
public class AhoCorasickPrefilterTest {

  @Test
  public void testScanSinglePattern() {
    final List<LiteralHint> hints = Arrays.asList(new LiteralHint(1, "foo", 0, false, false, 0));
    final AhoCorasickPrefilter prefilter = new AhoCorasickPrefilter(hints);
    final List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan("hello foo world");

    assertEquals(1, candidates.size());
    final AhoCorasickPrefilter.Candidate candidate = candidates.get(0);
    assertEquals(1, candidate.patternId);
    assertEquals(9, candidate.endIndexExclusive); // "foo" ends at position 9
    assertEquals(3, candidate.literalLength);
    assertEquals(6, candidate.startIndexForMatch()); // literal starts at 6, offset 0
  }

  @Test
  public void testScanMultiplePatterns() {
    final List<LiteralHint> hints =
        Arrays.asList(
            new LiteralHint(1, "foo", 0, false, false, 0),
            new LiteralHint(2, "bar", 5, false, false, 0));
    final AhoCorasickPrefilter prefilter = new AhoCorasickPrefilter(hints);
    final List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan("foo and bar test");

    assertEquals(2, candidates.size());

    // Find foo candidate
    final AhoCorasickPrefilter.Candidate fooCand =
        candidates.stream().filter(c -> c.patternId == 1).findFirst().orElse(null);
    assertNotNull(fooCand);
    assertEquals(3, fooCand.endIndexExclusive);
    assertEquals(3, fooCand.literalLength);

    // Find bar candidate
    final AhoCorasickPrefilter.Candidate barCand =
        candidates.stream().filter(c -> c.patternId == 2).findFirst().orElse(null);
    assertNotNull(barCand);
    assertEquals(11, barCand.endIndexExclusive);
    assertEquals(3, barCand.literalLength);
  }

  @Test
  public void testScanOverlappingMatches() {
    final List<LiteralHint> hints =
        Arrays.asList(
            new LiteralHint(1, "abc", 0, false, false, 0),
            new LiteralHint(2, "bcd", 0, false, false, 0));
    final AhoCorasickPrefilter prefilter = new AhoCorasickPrefilter(hints);
    final List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan("abcd");

    assertEquals(2, candidates.size());
    // Should find both "abc" and "bcd" in "abcd"
    assertTrue(candidates.stream().anyMatch(c -> c.patternId == 1));
    assertTrue(candidates.stream().anyMatch(c -> c.patternId == 2));
  }

  @Test
  public void testScanCaseInsensitive() {
    final List<LiteralHint> hints = Arrays.asList(new LiteralHint(1, "FOO", 0, false, true, 0));
    final AhoCorasickPrefilter prefilter = new AhoCorasickPrefilter(hints);

    // Should match both upper and lower case
    List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan("hello FOO world");
    assertEquals(1, candidates.size());

    candidates = prefilter.scan("hello foo world");
    assertEquals(1, candidates.size());
  }

  @Test
  public void testScanNoMatches() {
    final List<LiteralHint> hints = Arrays.asList(new LiteralHint(1, "foo", 0, false, false, 0));
    final AhoCorasickPrefilter prefilter = new AhoCorasickPrefilter(hints);
    final List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan("hello bar world");

    assertEquals(0, candidates.size());
  }

  @Test
  public void testScanMultipleInstancesSamePattern() {
    final List<LiteralHint> hints = Arrays.asList(new LiteralHint(1, "foo", 0, false, false, 0));
    final AhoCorasickPrefilter prefilter = new AhoCorasickPrefilter(hints);
    final List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan("foo bar foo baz foo");

    assertEquals(3, candidates.size());
    for (final AhoCorasickPrefilter.Candidate candidate : candidates) {
      assertEquals(1, candidate.patternId);
      assertEquals(3, candidate.literalLength);
    }
  }

  @Test
  public void testStartIndexForMatchWithOffset() {
    final List<LiteralHint> hints =
        Arrays.asList(
            new LiteralHint(1, "bar", 4, false, false, 2)); // literal at offset 2 in match
    final AhoCorasickPrefilter prefilter = new AhoCorasickPrefilter(hints);
    final List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan("foobar");

    assertEquals(1, candidates.size());
    final AhoCorasickPrefilter.Candidate candidate = candidates.get(0);
    assertEquals(6, candidate.endIndexExclusive); // "bar" ends at position 6
    assertEquals(3, candidate.literalLength);
    assertEquals(2, candidate.literalOffsetInMatch);
    assertEquals(1, candidate.startIndexForMatch()); // (6-3)-2 = 1
  }

  @Test
  public void testEmptyHints() {
    final AhoCorasickPrefilter prefilter = new AhoCorasickPrefilter(Arrays.asList());
    final List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan("hello world");

    assertEquals(0, candidates.size());
  }

  @Test
  public void testSameLiteralMultiplePatterns() {
    // Multiple patterns can share the same literal
    final List<LiteralHint> hints =
        Arrays.asList(
            new LiteralHint(1, "test", 0, false, false, 0),
            new LiteralHint(2, "test", 5, false, false, 1));
    final AhoCorasickPrefilter prefilter = new AhoCorasickPrefilter(hints);
    final List<AhoCorasickPrefilter.Candidate> candidates = prefilter.scan("testing");

    assertEquals(2, candidates.size()); // Same literal should generate candidates for both patterns
    assertTrue(candidates.stream().anyMatch(c -> c.patternId == 1));
    assertTrue(candidates.stream().anyMatch(c -> c.patternId == 2));
  }
}
