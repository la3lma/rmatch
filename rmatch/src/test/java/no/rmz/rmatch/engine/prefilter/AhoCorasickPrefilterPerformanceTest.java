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
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.junit.jupiter.api.Test;

final class AhoCorasickPrefilterPerformanceTest {

  @Test
  void optimizedScanAvoidsSubstringCostOnLargeInputs() {
    final int patternCount = 10_000;
    final List<LiteralHint> hints =
        IntStream.range(0, patternCount)
            .mapToObj(i -> new LiteralHint(i, literalFor(i), 0, false, false, 0))
            .toList();

    final String corpus = buildCorpus(patternCount, 60);

    final AhoCorasickPrefilter optimized = new AhoCorasickPrefilter(hints);
    final LegacyPrefilter legacy = new LegacyPrefilter(hints);

    // Warm-up to stabilize JVM effects.
    optimized.scan(corpus);
    legacy.scan(corpus);

    final long optimizedNanos = measureTotal(optimized::scan, corpus, 5);
    final long legacyNanos = measureTotal(legacy::scan, corpus, 5);

    // Same candidates, faster scan.
    final List<AhoCorasickPrefilter.Candidate> optimizedCandidates = optimized.scan(corpus);
    final List<AhoCorasickPrefilter.Candidate> legacyCandidates = legacy.scan(corpus);
    assertEquals(legacyCandidates.size(), optimizedCandidates.size());

    final double improvement = ((double) legacyNanos - optimizedNanos) / legacyNanos;
    System.out.printf(
        Locale.ROOT,
        "Legacy=%.2f ms, Optimized=%.2f ms, Improvement=%.2f%%%n",
        legacyNanos / 1_000_000.0,
        optimizedNanos / 1_000_000.0,
        improvement * 100.0);
    assertTrue(
        improvement > 0.05,
        () ->
            "Optimized scan should be at least 5% faster; improvement="
                + String.format("%.2f%%", improvement * 100));
  }

  private static long measureTotal(
      final Function<String, List<AhoCorasickPrefilter.Candidate>> scanner,
      final String corpus,
      final int iterations) {
    long total = 0L;
    for (int i = 0; i < iterations; i++) {
      final long start = System.nanoTime();
      scanner.apply(corpus);
      total += System.nanoTime() - start;
    }
    return total;
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

  private static final class LegacyPrefilter {
    private final Trie trie;
    private final Map<String, List<LiteralHint>> buckets = new HashMap<>();

    LegacyPrefilter(final Collection<LiteralHint> hints) {
      final Trie.TrieBuilder builder = Trie.builder();
      for (final LiteralHint hint : hints) {
        for (final String key : keyedLiterals(hint)) {
          buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(hint);
          builder.addKeyword(key);
        }
      }
      this.trie = builder.build();
    }

    List<AhoCorasickPrefilter.Candidate> scan(final CharSequence text) {
      final String s = text.toString();
      final List<AhoCorasickPrefilter.Candidate> out = new ArrayList<>();
      for (final Emit emit : trie.parseText(s)) {
        final String lit = s.substring(emit.getStart(), emit.getEnd() + 1);
        final List<LiteralHint> hints = buckets.get(lit);
        if (hints == null) {
          continue;
        }
        final int endIdxExclusive = emit.getEnd() + 1;
        for (final LiteralHint hint : hints) {
          out.add(
              new AhoCorasickPrefilter.Candidate(
                  hint.patternId(), endIdxExclusive, lit.length(), hint.literalOffsetInMatch()));
        }
      }
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
