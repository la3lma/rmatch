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
package no.rmz.rmatch.engine.prefilter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.TreeSet;
import no.rmz.rmatch.Matcher;
import no.rmz.rmatch.impls.TestMatchers;
import no.rmz.rmatch.utils.RegexStringBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the prefilter dropping short-form matches of optional-suffix patterns.
 *
 * <p>Discovered by CI when test-class ordering on the runner froze the prefilter activation
 * threshold at 1 for the whole JVM (it was read into a static field at class-load time), which
 * activated the literal prefilter for tiny pattern sets. With the prefilter active, {@code ab?}
 * failed to report its length-1 match on {@code "a"}: only positions seeded by the extracted
 * literal survived, and the extraction treated the optional {@code b} as required.
 *
 * <p>This test forces the prefilter on with threshold 1 explicitly, so the soundness requirement
 * holds regardless of test ordering: enabling the prefilter must never change the match set.
 */
public class PrefilterOptionalSuffixRegressionTest {

  private String oldThreshold;
  private String oldPrefilter;

  @BeforeEach
  public void forcePrefilterOn() {
    oldThreshold = System.getProperty("rmatch.prefilter.threshold");
    oldPrefilter = System.getProperty("rmatch.prefilter");
    System.setProperty("rmatch.prefilter.threshold", "1");
    System.setProperty("rmatch.prefilter", "aho");
  }

  @AfterEach
  public void restoreProperties() {
    restore("rmatch.prefilter.threshold", oldThreshold);
    restore("rmatch.prefilter", oldPrefilter);
  }

  private static void restore(final String key, final String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }

  private static Set<String> matchesOf(final String pattern, final String input) throws Exception {
    try (Matcher m = TestMatchers.newSingleMatcher()) {
      final Set<String> found = new TreeSet<>();
      m.add(
          pattern,
          (b, start, end) -> {
            synchronized (found) {
              // Expectations below use inclusive spans, like the semantics suites.
              found.add(start + "-" + (end - 1));
            }
          });
      m.match(new RegexStringBuffer(input));
      return found;
    }
  }

  @Test
  public void optionalSuffixShortFormSurvivesPrefilter() throws Exception {
    assertEquals(Set.of("0-0"), matchesOf("ab?", "a"));
  }

  @Test
  public void optionalSuffixBothFormsSurvivePrefilter() throws Exception {
    assertEquals(Set.of("0-0", "2-3"), matchesOf("ab?", "a ab"));
  }

  @Test
  public void starSuffixShortFormSurvivesPrefilter() throws Exception {
    assertEquals(Set.of("0-0"), matchesOf("ab*", "a"));
  }

  @Test
  public void optionalInMiddleSurvivesPrefilter() throws Exception {
    assertEquals(Set.of("0-2", "4-5"), matchesOf("ab?c", "abc ac"));
  }
}
