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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class AhoCorasickPrefilterPerformanceTest {

  @Test
  void optimizedScanAvoidsSubstringCostOnLargeInputs() {
    final int patternCount = 1_000;
    final List<LiteralHint> hints =
        IntStream.range(0, patternCount)
            .mapToObj(i -> new LiteralHint(i, literalFor(i), 0, false, false, 0))
            .toList();

    final String corpus = buildCorpus(patternCount, 20);

    final AhoCorasickPrefilter internal = new AhoCorasickPrefilter(hints);
    final NaivePrefilter naive = new NaivePrefilter(hints);

    // Warm-up to stabilize JVM effects.
    for (int i = 0; i < 3; i++) {
      internal.scan(corpus);
      naive.scan(corpus);
    }

    // Alternate call order to reduce warm-cache and JIT bias.
    final List<Long> internalRuns = new ArrayList<>();
    final List<Long> naiveRuns = new ArrayList<>();
    final int rounds = 15;
    for (int i = 0; i < rounds; i++) {
      if ((i & 1) == 0) {
        naiveRuns.add(measureOnce(naive::scan, corpus));
        internalRuns.add(measureOnce(internal::scan, corpus));
      } else {
        internalRuns.add(measureOnce(internal::scan, corpus));
        naiveRuns.add(measureOnce(naive::scan, corpus));
      }
    }

    final long internalNanos = median(internalRuns);
    final long naiveNanos = median(naiveRuns);

    // Same candidates, faster scan.
    final List<AhoCorasickPrefilter.Candidate> internalCandidates = sorted(internal.scan(corpus));
    final List<AhoCorasickPrefilter.Candidate> naiveCandidates = sorted(naive.scan(corpus));
    assertEquals(naiveCandidates, internalCandidates);

    final double improvement = ((double) naiveNanos - internalNanos) / naiveNanos;
    System.out.printf(
        Locale.ROOT,
        "Naive=%.2f ms, Internal Aho-Corasick=%.2f ms, Improvement=%.2f%%%n",
        naiveNanos / 1_000_000.0,
        internalNanos / 1_000_000.0,
        improvement * 100.0);
    assertTrue(
        internalNanos <= (long) (naiveNanos * 0.50),
        () ->
            "Internal Aho-Corasick should comfortably beat naive scanning; improvement="
                + String.format("%.2f%%", improvement * 100));
  }

  private static long measureOnce(
      final Function<String, List<AhoCorasickPrefilter.Candidate>> scanner, final String corpus) {
    final long start = System.nanoTime();
    scanner.apply(corpus);
    return System.nanoTime() - start;
  }

  private static long median(final List<Long> values) {
    final List<Long> sorted = new ArrayList<>(values);
    sorted.sort(Long::compareTo);
    return sorted.get(sorted.size() / 2);
  }

  private static List<AhoCorasickPrefilter.Candidate> sorted(
      final List<AhoCorasickPrefilter.Candidate> candidates) {
    final List<AhoCorasickPrefilter.Candidate> sorted = new ArrayList<>(candidates);
    sorted.sort(AhoCorasickPrefilterPerformanceTest::compareCandidate);
    return sorted;
  }

  private static int compareCandidate(
      final AhoCorasickPrefilter.Candidate a, final AhoCorasickPrefilter.Candidate b) {
    final int byEnd = Integer.compare(a.endIndexExclusive(), b.endIndexExclusive());
    if (byEnd != 0) {
      return byEnd;
    }
    final int byLength = Integer.compare(b.literalLength(), a.literalLength());
    if (byLength != 0) {
      return byLength;
    }
    return Integer.compare(a.patternId(), b.patternId());
  }

  private static String buildCorpus(final int patternCount, final int repeats) {
    final StringBuilder sb = new StringBuilder(patternCount * repeats * 8);
    for (int r = 0; r < repeats; r++) {
      for (int i = 0; i < patternCount; i++) {
        sb.append(literalFor(i)).append(' ');
        if ((i & 7) == 0) {
          sb.append("padding block ");
        }
      }
    }
    return sb.toString();
  }

  private static String literalFor(final int i) {
    final String suffix = Integer.toString(i);
    return "literal-" + suffix + "-payload-" + suffix + "-segment";
  }

  private static final class NaivePrefilter {
    private final Map<String, List<LiteralHint>> buckets = new HashMap<>();

    NaivePrefilter(final Collection<LiteralHint> hints) {
      for (final LiteralHint hint : hints) {
        for (final String key : keyedLiterals(hint)) {
          buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(hint);
        }
      }
    }

    List<AhoCorasickPrefilter.Candidate> scan(final CharSequence text) {
      final String s = text.toString();
      final List<AhoCorasickPrefilter.Candidate> out = new ArrayList<>();
      for (final Map.Entry<String, List<LiteralHint>> entry : buckets.entrySet()) {
        final String lit = entry.getKey();
        int start = s.indexOf(lit);
        while (start >= 0) {
          final int endIdxExclusive = start + lit.length();
          for (final LiteralHint hint : entry.getValue()) {
            out.add(
                new AhoCorasickPrefilter.Candidate(
                    hint.patternId(), endIdxExclusive, lit.length(), hint.literalOffsetInMatch()));
          }
          start = s.indexOf(lit, start + 1);
        }
      }
      out.sort(AhoCorasickPrefilterPerformanceTest::compareCandidate);
      return out;
    }
  }

  private static List<String> keyedLiterals(final LiteralHint hint) {
    if (!hint.caseInsensitive()) {
      return List.of(hint.literal());
    }
    final String lower = hint.literal().toLowerCase(Locale.ROOT);
    final String upper = hint.literal().toUpperCase(Locale.ROOT);
    if (lower.equals(upper)) {
      return List.of(lower);
    }
    if (lower.length() == upper.length()) {
      return List.of(lower, upper);
    }
    return List.of(lower);
  }
}
