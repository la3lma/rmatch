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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.utils.RegexStringBuffer;
import org.junit.jupiter.api.Test;

/**
 * Correctness invariants for prefiltering.
 *
 * <p>For identical pattern/text workloads, prefilter ON and OFF must produce identical match
 * outputs. This guards against false negatives introduced by heuristic prefilter decisions.
 */
public class PrefilterCorrectnessInvariantTest {
  private record ObservedMatch(String regex, int start, int end) {}

  @Test
  public void curatedScenariosPrefilterOnAndOffProduceSameMatches() throws RegexpParserException {
    final List<String> patterns =
        List.of(
            "there", "zxq", "foo.*bar", "abc|def", "test.com", "foo[a-z]+bar", "hello", "world");

    final String text =
        "hello there, this is zxq and test.com\n"
            + "foo---bar\n"
            + "abc and def in one line\n"
            + "fooquxbar in another line\n"
            + "the world";

    final List<ObservedMatch> off = runMatcher(text, patterns, false);
    final List<ObservedMatch> on = runMatcher(text, patterns, true);
    assertEquals(off, on, "Prefilter ON/OFF mismatch in curated scenario");
    assertFalse(on.isEmpty(), "Curated scenario should generate at least one match");
  }

  @Test
  public void differentialFuzzPrefilterOnAndOffProduceSameMatches() throws RegexpParserException {
    final Random rnd = new Random(1337L);
    for (int iteration = 0; iteration < 40; iteration++) {
      final List<String> patterns = generatePatternSet(rnd, 28);
      final String text = generateCorpus(rnd, 220);

      final List<ObservedMatch> off = runMatcher(text, patterns, false);
      final List<ObservedMatch> on = runMatcher(text, patterns, true);

      assertEquals(
          off,
          on,
          "Prefilter ON/OFF mismatch at iteration=" + iteration + ", patterns=" + patterns);
    }
  }

  private static List<ObservedMatch> runMatcher(
      final String text, final List<String> patterns, final boolean prefilterEnabled)
      throws RegexpParserException {
    final String oldPrefilter = System.getProperty("rmatch.prefilter");
    final String oldThreshold = System.getProperty("rmatch.prefilter.threshold");
    final String oldEngine = System.getProperty("rmatch.engine");

    try {
      System.setProperty("rmatch.engine", "fastpath");
      System.setProperty("rmatch.prefilter", prefilterEnabled ? "aho" : "off");
      System.setProperty("rmatch.prefilter.threshold", "1");

      final MatcherImpl matcher = new MatcherImpl();
      final List<ObservedMatch> observed = new ArrayList<>();
      for (final String pattern : patterns) {
        matcher.add(
            pattern, (b, start, end) -> observed.add(new ObservedMatch(pattern, start, end)));
      }

      matcher.match(new RegexStringBuffer(text));
      observed.sort(
          Comparator.comparing(ObservedMatch::regex)
              .thenComparingInt(ObservedMatch::start)
              .thenComparingInt(ObservedMatch::end));
      return observed;
    } finally {
      restoreSystemProperty("rmatch.prefilter", oldPrefilter);
      restoreSystemProperty("rmatch.prefilter.threshold", oldThreshold);
      restoreSystemProperty("rmatch.engine", oldEngine);
    }
  }

  private static List<String> generatePatternSet(final Random rnd, final int targetCount) {
    final Set<String> patterns = new LinkedHashSet<>();
    while (patterns.size() < targetCount) {
      final String a = randomLiteral(rnd, 2 + rnd.nextInt(5));
      final String b = randomLiteral(rnd, 2 + rnd.nextInt(5));
      switch (rnd.nextInt(6)) {
        case 0 -> patterns.add(a);
        case 1 -> patterns.add(a + ".*" + b);
        case 2 -> patterns.add(a + b);
        case 3 -> patterns.add(a + "|" + b);
        case 4 -> patterns.add(a + "[a-z]+" + b);
        default -> patterns.add(a + "." + b);
      }
    }
    return new ArrayList<>(patterns);
  }

  private static String generateCorpus(final Random rnd, final int tokenCount) {
    final StringBuilder sb = new StringBuilder(tokenCount * 7);
    for (int i = 0; i < tokenCount; i++) {
      if (i > 0) {
        sb.append(rnd.nextBoolean() ? ' ' : '\n');
      }
      if (rnd.nextInt(10) == 0) {
        sb.append("there");
      } else if (rnd.nextInt(12) == 0) {
        sb.append("zxq");
      } else {
        sb.append(randomLiteral(rnd, 2 + rnd.nextInt(6)));
      }
    }
    return sb.toString();
  }

  private static String randomLiteral(final Random rnd, final int length) {
    final String alphabet = "abcdefghijklmnopqrstuvwxyz";
    final StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
    }
    return sb.toString();
  }

  private static void restoreSystemProperty(final String key, final String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
