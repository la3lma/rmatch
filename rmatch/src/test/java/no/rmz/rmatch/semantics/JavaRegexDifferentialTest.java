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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.utils.RegexStringBuffer;
import org.junit.jupiter.api.Test;

/**
 * Seeded-random differential test against java.util.regex.
 *
 * <p>ORACLE: rmatch's semantic rule is "for every start position, report the longest match starting
 * there" (see BasicMatchingSemanticsTest). The java oracle for a given start i is therefore: the
 * longest end &gt; i such that {@code pattern.matcher(input.substring(i, end)). matches()} — tried
 * longest-first. Full-match semantics make the oracle immune to java's ordered-alternation and
 * greediness quirks.
 *
 * <p>ZERO-LENGTH matches are excluded from comparison on both sides: their reporting semantics are
 * engine-defined and not yet specified for rmatch (see the syntax roadmap).
 *
 * <p>Deterministic seed: failures reproduce; each failure prints a minimal (pattern, input) repro
 * line.
 */
public class JavaRegexDifferentialTest {

  private static final long SEED = 20260705L;
  private static final int PATTERN_COUNT = 120;
  private static final int INPUTS_PER_PATTERN = 12;
  private static final char[] ALPHABET = {'a', 'b', 'c'};
  private static final char[] INPUT_ALPHABET = {'a', 'b', 'c', ' '};

  /** Generate a random atom over the supported syntax subset. */
  private static String atom(final Random rng) {
    return switch (rng.nextInt(6)) {
      case 0 -> String.valueOf(ALPHABET[rng.nextInt(ALPHABET.length)]);
      case 1 -> ".";
      case 2 ->
          "["
              + ALPHABET[rng.nextInt(ALPHABET.length)]
              + ALPHABET[rng.nextInt(ALPHABET.length)]
              + "]";
      case 3 -> "[^" + ALPHABET[rng.nextInt(ALPHABET.length)] + "]";
      case 4 ->
          // F1: a parenthesized group with a small alternation inside — a quantifiable atom.
          "("
              + ALPHABET[rng.nextInt(ALPHABET.length)]
              + "|"
              + ALPHABET[rng.nextInt(ALPHABET.length)]
              + ALPHABET[rng.nextInt(ALPHABET.length)]
              + ")";
      default -> String.valueOf(ALPHABET[rng.nextInt(ALPHABET.length)]);
    };
  }

  private static String pattern(final Random rng) {
    final StringBuilder sb = new StringBuilder();
    final int atoms = 1 + rng.nextInt(4);
    for (int i = 0; i < atoms; i++) {
      sb.append(atom(rng));
      final int q = rng.nextInt(6);
      if (q == 1) {
        sb.append('?');
      } else if (q == 2) {
        sb.append('*');
      } else if (q == 3) {
        sb.append('+');
      } else if (q == 4) {
        // F3: counted quantifiers.
        final int min = rng.nextInt(3);
        final int kind = rng.nextInt(3);
        if (kind == 0 && min > 0) {
          sb.append('{').append(min).append('}');
        } else if (kind == 1) {
          sb.append('{').append(min).append(',').append(min + 1 + rng.nextInt(2)).append('}');
        } else {
          sb.append('{').append(Math.max(min, 1)).append(",}");
        }
      }
    }
    if (rng.nextInt(4) == 0) {
      // One alternation, second branch a simple literal run.
      sb.append('|');
      final int len = 1 + rng.nextInt(3);
      for (int i = 0; i < len; i++) {
        sb.append(ALPHABET[rng.nextInt(ALPHABET.length)]);
      }
    }
    return sb.toString();
  }

  private static String input(final Random rng) {
    final int len = rng.nextInt(13);
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < len; i++) {
      sb.append(INPUT_ALPHABET[rng.nextInt(INPUT_ALPHABET.length)]);
    }
    return sb.toString();
  }

  /** Per-start-longest oracle via java.util.regex full-match, longest end first. */
  private static Set<String> javaOracle(final String pattern, final String input) {
    final java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
    final Set<String> out = new TreeSet<>();
    for (int start = 0; start < input.length(); start++) {
      for (int end = input.length(); end > start; end--) {
        if (p.matcher(input.substring(start, end)).matches()) {
          out.add(start + "-" + (end - 1));
          break;
        }
      }
    }
    return out;
  }

  private static Set<String> rmatchMatches(final String pattern, final String input)
      throws Exception {
    final Matcher m = new MatcherImpl(new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
    final Set<String> found = new TreeSet<>();
    m.add(
        pattern,
        (b, start, end) -> {
          if (end > start || (end == start)) {
            // Exclude zero-length matches from comparison (engine-defined semantics).
            if (end >= start && !(end == start && matchIsZeroWidth(b, start, end, pattern))) {
              synchronized (found) {
                found.add(start + "-" + end);
              }
            }
          }
        });
    m.match(new RegexStringBuffer(input));
    m.shutdown();
    return found;
  }

  /**
   * A length-1 match has end == start; a zero-width one would too — rmatch reports inclusive ends,
   * so end==start is a ONE-character match, never zero-width. Zero-width matches simply do not
   * occur in rmatch's reporting (no known reporting path), so nothing to filter in practice.
   */
  private static boolean matchIsZeroWidth(
      final Object b, final int start, final int end, final String pattern) {
    return false;
  }

  @Test
  public void randomPatternsAgreeWithJavaOracle() throws Exception {
    final Random rng = new Random(SEED);
    final List<String> failures = new ArrayList<>();
    int comparisons = 0;

    for (int pi = 0; pi < PATTERN_COUNT; pi++) {
      final String pattern = pattern(rng);
      for (int ii = 0; ii < INPUTS_PER_PATTERN; ii++) {
        final String input = input(rng);
        final Set<String> expected = javaOracle(pattern, input);
        final Set<String> actual = rmatchMatches(pattern, input);
        comparisons++;
        if (!expected.equals(actual)) {
          failures.add(
              "REPRO pattern=<"
                  + pattern
                  + "> input=<"
                  + input
                  + "> java="
                  + expected
                  + " rmatch="
                  + actual);
        }
      }
    }

    if (!failures.isEmpty()) {
      final StringBuilder msg =
          new StringBuilder("Differential failures: " + failures.size() + "/" + comparisons + "\n");
      failures.stream().limit(15).forEach(f -> msg.append(f).append('\n'));
      assertEquals(List.of(), failures.subList(0, Math.min(failures.size(), 15)), msg.toString());
    }
  }
}
