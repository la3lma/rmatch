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
 * F2 of the syntax program: escapes and shorthand character classes. Written test-first.
 *
 * <p>Also covers KB-2: parseQuotedChar had an inverted condition making EVERY escape throw at add()
 * time.
 */
public class EscapeSyntaxTest {

  private static Set<String> matchesOf(final String pattern, final String input) throws Exception {
    final Matcher m = TestMatchers.newSingleMatcher();
    final Set<String> found = new TreeSet<>();
    m.add(
        pattern,
        (b, start, end) -> {
          synchronized (found) {
            found.add(start + "-" + end);
          }
        });
    m.match(new RegexStringBuffer(input));
    m.shutdown();
    return found;
  }

  // --- KB-2: escapes must parse at all

  @Test
  public void escapedLiteralDotIsNotAnyChar() throws Exception {
    assertEquals(Set.of("4-6"), matchesOf("a\\.b", "axb a.b"));
  }

  @Test
  public void escapedBackslash() throws Exception {
    assertEquals(Set.of("1-1"), matchesOf("\\\\", "a\\b"));
  }

  @Test
  public void escapedQuantifierChars() throws Exception {
    assertEquals(Set.of("0-1"), matchesOf("a\\+", "a+ ab"));
    assertEquals(Set.of("0-1"), matchesOf("a\\*", "a* ab"));
    assertEquals(Set.of("0-1"), matchesOf("a\\?", "a? ab"));
  }

  @Test
  public void escapedBracketsAndParens() throws Exception {
    assertEquals(Set.of("0-1"), matchesOf("\\[a", "[a"));
    assertEquals(Set.of("0-1"), matchesOf("\\(a", "(a"));
  }

  @Test
  public void controlEscapes() throws Exception {
    assertEquals(Set.of("1-1"), matchesOf("\\t", "a\tb"));
    assertEquals(Set.of("1-1"), matchesOf("\\n", "a\nb"));
  }

  @Test
  public void escapedLiteralWithQuantifierBindsToLastAtom() throws Exception {
    // KB-1 discipline: quantifier binds to the escaped atom only.
    assertEquals(Set.of("0-0", "2-3"), matchesOf("a\\.?", "a a."));
  }

  // --- Shorthand classes

  @Test
  public void digitClass() throws Exception {
    assertEquals(Set.of("1-1", "3-3"), matchesOf("\\d", "a1b2"));
  }

  @Test
  public void digitClassQuantified() throws Exception {
    assertEquals(Set.of("1-3", "2-3", "3-3"), matchesOf("\\d+", "a123b"));
  }

  @Test
  public void nonDigitClass() throws Exception {
    assertEquals(Set.of("0-0", "2-2"), matchesOf("\\D", "a1b2"));
  }

  @Test
  public void wordClass() throws Exception {
    assertEquals(Set.of("0-0", "2-2", "3-3"), matchesOf("\\w", "a _2 "));
  }

  @Test
  public void nonWordClass() throws Exception {
    assertEquals(Set.of("1-1", "4-4"), matchesOf("\\W", "a _2 "));
  }

  @Test
  public void whitespaceClass() throws Exception {
    assertEquals(Set.of("1-1", "3-3"), matchesOf("\\s", "a b\tc"));
  }

  @Test
  public void nonWhitespaceClassQuantified() throws Exception {
    assertEquals(Set.of("0-1", "1-1", "3-4", "4-4"), matchesOf("\\S+", "ab cd"));
  }

  @Test
  public void classInSequence() throws Exception {
    assertEquals(Set.of("0-2"), matchesOf("a\\db", "a1b a b"));
  }

  @Test
  public void classEscapeInsideCharset() throws Exception {
    // [\d] behaves like \d; [\t ] matches tab or space.
    assertEquals(Set.of("1-1"), matchesOf("[\\d]", "a1b"));
    assertEquals(Set.of("1-1", "3-3"), matchesOf("[\\t ]", "a\tb c"));
  }

  // --- Errors

  @Test
  public void unknownEscapeThrows() {
    assertThrows(RegexpParserException.class, () -> matchesOf("\\q", "q"));
  }

  @Test
  public void trailingBackslashThrows() {
    assertThrows(RegexpParserException.class, () -> matchesOf("ab\\", "ab"));
  }
}
