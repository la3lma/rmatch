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
package no.rmz.rmatch.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.TreeSet;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.RegexpParserException;
import no.rmz.rmatch.impls.TestMatchers;
import no.rmz.rmatch.utils.RegexStringBuffer;
import org.junit.jupiter.api.Test;

/**
 * F3 of the syntax program: counted quantifiers {m}, {m,n} and {m,}. Written test-first.
 *
 * <p>Compiled by expansion into existing operators; the expansion bound is capped to keep NDFA
 * sizes sane.
 */
public class CountedQuantifierTest {

  private static Set<String> matchesOf(final String pattern, final String input) throws Exception {
    final Matcher m = TestMatchers.newSingleMatcher();
    final Set<String> found = new TreeSet<>();
    m.add(
        pattern,
        (b, start, end) -> {
          synchronized (found) {
            // Callback end is exclusive; expectation tables use inclusive spans.
            found.add(start + "-" + (end - 1));
          }
        });
    m.match(new RegexStringBuffer(input));
    m.close();
    return found;
  }

  @Test
  public void exactCount() throws Exception {
    // a{3} on "aaaa": per-start-longest, exactly 3 a's from starts 0 and 1.
    assertEquals(Set.of("0-2", "1-3"), matchesOf("a{3}", "aaaa"));
  }

  @Test
  public void exactCountOnChar() throws Exception {
    assertEquals(Set.of("1-2"), matchesOf("b{2}", "abba"));
  }

  @Test
  public void rangeCount() throws Exception {
    // a{2,3} on "aaaa": start0 longest 3 (0-2), start1 longest 3 (1-3), start2 only 2 (2-3).
    assertEquals(Set.of("0-2", "1-3", "2-3"), matchesOf("a{2,3}", "aaaa"));
  }

  @Test
  public void openEndedCount() throws Exception {
    // a{2,} on "aaaa": longest per start with at least 2.
    assertEquals(Set.of("0-3", "1-3", "2-3"), matchesOf("a{2,}", "aaaa"));
  }

  @Test
  public void countOnCharset() throws Exception {
    assertEquals(Set.of("0-3"), matchesOf("[ab]{4}", "abba"));
  }

  @Test
  public void countOnShorthandClass() throws Exception {
    assertEquals(Set.of("1-4"), matchesOf("\\d{4}", "x2026y"));
  }

  @Test
  public void countOnGroup() throws Exception {
    assertEquals(Set.of("0-5", "2-5"), matchesOf("(ab){2,3}", "ababab"));
  }

  @Test
  public void countInSequence() throws Exception {
    assertEquals(Set.of("0-4"), matchesOf("xa{2}bx", "xaabx"));
  }

  @Test
  public void countBindsToLastAtomOnly() throws Exception {
    // KB-1 discipline: ab{2} means a(b{2}).
    assertEquals(Set.of("0-2"), matchesOf("ab{2}", "abb ab"));
  }

  @Test
  public void literalBraceWithoutDigitsIsLiteral() throws Exception {
    // A '{' not opening a valid counted quantifier is a literal, as in java.
    assertEquals(Set.of("0-2"), matchesOf("a\\{b", "a{b"));
  }

  @Test
  public void zeroMinimum() throws Exception {
    // a{0,2}b: matches b alone, ab, aab.
    assertEquals(Set.of("0-2", "1-2", "2-2"), matchesOf("a{0,2}b", "aab"));
  }

  @Test
  public void malformedCountThrows() {
    assertThrows(RegexpParserException.class, () -> matchesOf("a{2,1}", "aa"));
    assertThrows(RegexpParserException.class, () -> matchesOf("a{", "a"));
    assertThrows(RegexpParserException.class, () -> matchesOf("a{x}", "a"));
  }

  @Test
  public void excessiveCountThrows() {
    assertThrows(RegexpParserException.class, () -> matchesOf("a{100000}", "aa"));
  }
}
