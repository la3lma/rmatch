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
 * F4 of the syntax program: case-insensitive matching via a "(?i)" pattern prefix (and "(?s)"
 * accepted as a documented no-op, since '.' already matches newline). Written test-first.
 *
 * <p>Prefix-only by design: pattern identity in RegexpStorage is the raw string, so the flag lives
 * naturally in the pattern. Scoped inline flags are future work.
 */
public class CaseInsensitiveTest {

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
  public void literalFolding() throws Exception {
    assertEquals(Set.of("0-2", "4-6", "8-10"), matchesOf("(?i)cat", "cat CAT cAt"));
  }

  @Test
  public void plainPatternsRemainSensitive() throws Exception {
    assertEquals(Set.of("0-2"), matchesOf("cat", "cat CAT"));
  }

  @Test
  public void charsetMembersFold() throws Exception {
    assertEquals(Set.of("0-1", "3-4"), matchesOf("(?i)[ab]x", "Ax bX cx"));
  }

  @Test
  public void rangeFolds() throws Exception {
    assertEquals(Set.of("0-1", "3-4"), matchesOf("(?i)[a-c]z", "Az bZ dz"));
  }

  @Test
  public void quantifiersAndFoldingCompose() throws Exception {
    assertEquals(Set.of("0-3", "1-3", "2-3", "3-3"), matchesOf("(?i)a+", "aAaA"));
  }

  @Test
  public void groupsAndAlternationFold() throws Exception {
    assertEquals(Set.of("0-1", "3-4"), matchesOf("(?i)(ab|cd)", "AB cD xy"));
  }

  @Test
  public void escapedLiteralsFold() throws Exception {
    // The escaped char is a literal; folding still applies to letters.
    assertEquals(Set.of("0-1", "3-4"), matchesOf("(?i)a\\+", "A+ a+"));
  }

  @Test
  public void dotallFlagIsAcceptedNoOp() throws Exception {
    assertEquals(Set.of("0-2"), matchesOf("(?s)a.b", "a\nb"));
  }

  @Test
  public void combinedFlags() throws Exception {
    assertEquals(Set.of("0-2"), matchesOf("(?is)a.b", "A\nb"));
  }

  @Test
  public void midPatternFlagThrows() {
    assertThrows(RegexpParserException.class, () -> matchesOf("ab(?i)cd", "abcd"));
  }

  @Test
  public void countedQuantifierUnderFolding() throws Exception {
    assertEquals(Set.of("0-2", "1-3"), matchesOf("(?i)a{3}", "aAaA"));
  }
}
