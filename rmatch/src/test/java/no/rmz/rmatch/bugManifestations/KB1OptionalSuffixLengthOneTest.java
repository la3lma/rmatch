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
package no.rmz.rmatch.bugManifestations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.TreeSet;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.impls.TestMatchers;
import no.rmz.rmatch.utils.RegexStringBuffer;
import org.junit.jupiter.api.Test;

/**
 * KB-1 (docs/labnotebook/KNOWN-BUGS.md): a pattern whose suffix is optional must produce its
 * shorter match when the optional part is absent. Minimal manifestation: "ab?" must match a lone
 * "a". Suspected root cause: epsilon/terminal wiring for trailing quantifiers in the compiler — the
 * state after consuming 'a' should be terminal (b? can match empty), but apparently is not.
 *
 * <p>These tests are written to CORRECT semantics, so they are RED until KB-1 is fixed.
 */
public class KB1OptionalSuffixLengthOneTest {

  private static Set<String> matchesOf(final String[] patterns, final String input)
      throws Exception {
    final Matcher m = TestMatchers.newSingleMatcher();
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

  /** The minimal manifestation: ab? on "a" alone. */
  @Test
  public void optionalSuffixMatchesWithoutTheOptionalPart() throws Exception {
    assertEquals(Set.of("ab?@0-0"), matchesOf(new String[] {"ab?"}, "a"));
  }

  /** The original observation: both the short and the long match must fire. */
  @Test
  public void optionalSuffixMatchesBothForms() throws Exception {
    assertEquals(Set.of("ab?@0-0", "ab?@2-3"), matchesOf(new String[] {"ab?"}, "a ab"));
  }

  /** Same disease, star quantifier: ab* must match a lone "a" (b* matches empty). */
  @Test
  public void starSuffixMatchesWithoutTheStarPart() throws Exception {
    assertEquals(Set.of("ab*@0-0"), matchesOf(new String[] {"ab*"}, "a"));
  }

  /** Longest-match semantics with the star: on "abb" the match extends to the end. */
  @Test
  public void starSuffixExtends() throws Exception {
    assertEquals(Set.of("ab*@0-2"), matchesOf(new String[] {"ab*"}, "abb"));
  }

  /** Optional in the middle is unaffected disease-wise but pins the semantics. */
  @Test
  public void optionalInMiddle() throws Exception {
    assertEquals(Set.of("ab?c@0-2", "ab?c@4-5"), matchesOf(new String[] {"ab?c"}, "abc ac"));
  }
}
