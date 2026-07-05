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
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.utils.RegexStringBuffer;
import org.junit.jupiter.api.Test;

/**
 * F1 of the syntax program: grouping with ( ) and (?: ). Written test-first; red until grouping is
 * implemented. Expectations follow the per-start-longest rule (BasicMatchingSemanticsTest).
 */
public class GroupingSyntaxTest {

  private static Set<String> matchesOf(final String pattern, final String input) throws Exception {
    final Matcher m = new MatcherImpl(new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
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

  @Test
  public void plainGroupConcatenates() throws Exception {
    assertEquals(Set.of("0-2"), matchesOf("(ab)c", "abc"));
  }

  @Test
  public void groupWithAlternationInside() throws Exception {
    assertEquals(Set.of("0-1", "3-4"), matchesOf("(a|b)c", "ac bc cc"));
  }

  @Test
  public void quantifiedGroupOptional() throws Exception {
    // (ab)?c on "abc c": start0 "abc", start2 "c" (group absent), start4 "c".
    assertEquals(Set.of("0-2", "2-2", "4-4"), matchesOf("(ab)?c", "abc c"));
  }

  @Test
  public void quantifiedGroupPlusLoops() throws Exception {
    assertEquals(Set.of("0-5", "2-5", "4-5"), matchesOf("(ab)+", "ababab"));
  }

  @Test
  public void quantifiedGroupStar() throws Exception {
    assertEquals(Set.of("0-4"), matchesOf("a(bc)*", "abcbc"));
  }

  @Test
  public void nestedGroups() throws Exception {
    assertEquals(Set.of("0-2", "4-6"), matchesOf("(a(b|c))d", "abd acd"));
  }

  @Test
  public void nonCapturingGroupIsEquivalent() throws Exception {
    assertEquals(Set.of("0-2", "2-2", "4-4"), matchesOf("(?:ab)?c", "abc c"));
  }

  @Test
  public void groupFollowedByLiteralTail() throws Exception {
    // Quantifier binds to the group only, not to the tail (KB-1 discipline for groups).
    // On "abxy": group present 0-3; group absent, "xy" alone at 2-3.
    assertEquals(Set.of("0-3", "2-3"), matchesOf("(ab)?xy", "abxy"));
  }

  @Test
  public void alternationOfGroups() throws Exception {
    assertEquals(Set.of("0-1", "3-4"), matchesOf("(ab)|(cd)", "ab cd"));
  }

  @Test
  public void unbalancedOpenThrows() {
    assertThrows(RegexpParserException.class, () -> matchesOf("(ab", "ab"));
  }

  @Test
  public void unbalancedCloseThrows() {
    assertThrows(RegexpParserException.class, () -> matchesOf("ab)", "ab"));
  }

  @Test
  public void unsupportedGroupConstructThrows() {
    assertThrows(RegexpParserException.class, () -> matchesOf("(?=ab)", "ab"));
  }
}
