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
package no.rmz.rmatch.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.TreeSet;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.RegexpParserException;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.utils.RegexStringBuffer;
import org.junit.jupiter.api.Test;

/**
 * Edge-case tests for the two-character start-candidate filter in FastPathMatchEngine.
 *
 * <p>The filter must never lose matches. The dangerous corners are: complete length-1 matches
 * (where the pattern cannot consume a second character but must still fire), matches starting at
 * the last character of the buffer (no lookahead available), and patterns whose second character is
 * a wildcard or character class.
 */
public class TwoCharStartFilterTest {

  private static Set<String> matchesOf(final String[] patterns, final String input)
      throws RegexpParserException, InterruptedException {
    final Matcher m = new MatcherImpl(new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
    final Set<String> found = new TreeSet<>();
    for (final String p : patterns) {
      final String pattern = p;
      m.add(
          pattern,
          (b, start, end) -> {
            synchronized (found) {
              found.add(pattern + "@" + start + "-" + end);
            }
          });
    }
    m.match(new RegexStringBuffer(input));
    m.shutdown();
    return found;
  }

  /** A single-character pattern that can never continue must still match everywhere it occurs. */
  @Test
  public void lengthOneMatchesSurviveFiltering() throws Exception {
    final Set<String> found = matchesOf(new String[] {"I"}, "I saw It, I did");
    // 'I' occurs at 0, 6 (It) and 10; all must fire regardless of the following character.
    assertEquals(Set.of("I@0-0", "I@6-6", "I@10-10"), found);
  }

  /** A match starting at the very last character has no lookahead and must not be lost. */
  @Test
  public void matchAtLastBufferPositionIsFound() throws Exception {
    final Set<String> found = matchesOf(new String[] {"x", "xy"}, "aaax");
    assertEquals(Set.of("x@3-3"), found);
  }

  /** Wildcard second characters must not be filtered away. */
  @Test
  public void wildcardSecondCharacterSurvives() throws Exception {
    final Set<String> found = matchesOf(new String[] {"a.c"}, "abc axc a c");
    assertEquals(Set.of("a.c@0-2", "a.c@4-6", "a.c@8-10"), found);
  }

  /**
   * Differential test: the filtered fastpath engine must produce exactly the same matches as the
   * legacy engine (which has no two-character filter) on overlapping mixed-length patterns, where
   * domination semantics are at their most delicate.
   */
  @Test
  public void filteredEngineAgreesWithLegacyEngine() throws Exception {
    final String[] patterns = {"a", "ab", "abc", "b", "bc"};
    final String input = "ab abc a b bc abcabc";

    final Set<String> fastpath = matchesOf(patterns, input);

    final String oldEngine = System.getProperty("rmatch.engine");
    System.setProperty("rmatch.engine", "legacy");
    try {
      final Set<String> legacy = matchesOf(patterns, input);
      assertEquals(legacy, fastpath, "filtered fastpath and legacy engines must agree");
    } finally {
      if (oldEngine == null) {
        System.clearProperty("rmatch.engine");
      } else {
        System.setProperty("rmatch.engine", oldEngine);
      }
    }
  }

  /** Optional/quantified second characters: ab? style patterns keep matching. */
  @Test
  public void quantifiedPatternsSurvive() throws Exception {
    final Set<String> found = matchesOf(new String[] {"ab?"}, "a ab");
    // Both the length-1 match (optional suffix absent) and the length-2 match must fire.
    // The @0-0 match was missing until KB-1 (quantifier bound to the whole preceding literal
    // instead of the last atom) was fixed in SurfaceRegexpParser.
    assertEquals(Set.of("ab?@0-0", "ab?@2-3"), found);
  }
}
