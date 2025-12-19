package no.rmz.rmatch.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import no.rmz.rmatch.compiler.NDFACompilerImpl;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.utils.StringBuffer;
import org.junit.jupiter.api.Test;

final class FastPathPrefilterThresholdTest {

  @Test
  void skipsPrefilterBelowThresholdToReduceLatency() throws Exception {
    final String originalThreshold = System.getProperty("rmatch.prefilter.threshold");
    final String originalEngine = System.getProperty("rmatch.engine");
    final String originalPrefilter = System.getProperty("rmatch.prefilter");

    try {
      System.setProperty("rmatch.engine", "fastpath");
      System.setProperty("rmatch.prefilter", "aho");

      final List<String> patterns =
          IntStream.range(0, 64).mapToObj(i -> "literal-" + i + "-token").toList();
      final String corpus = buildCorpus(patterns);

      final MatchRunResult prefilterEnabled = measureMatchTime(patterns, corpus, 1, 5);
      final MatchRunResult prefilterSkipped = measureMatchTime(patterns, corpus, 10_000, 5);

      assertEquals(
          prefilterEnabled.matches(),
          prefilterSkipped.matches(),
          "Prefilter gating must not change match results");

      final double improvement =
          ((double) prefilterEnabled.nanos() - prefilterSkipped.nanos())
              / (double) prefilterEnabled.nanos();

      assertTrue(
          improvement > 0.10,
          () ->
              "Disabling prefilter for small pattern sets should cut latency by at least 10%; got "
                  + String.format("%.2f%%", improvement * 100));
    } finally {
      restoreProperty("rmatch.prefilter.threshold", originalThreshold);
      restoreProperty("rmatch.engine", originalEngine);
      restoreProperty("rmatch.prefilter", originalPrefilter);
    }
  }

  private static MatchRunResult measureMatchTime(
      final List<String> patterns, final String corpus, final int threshold, final int iterations)
      throws Exception {
    System.setProperty("rmatch.prefilter.threshold", Integer.toString(threshold));

    final Matcher matcher =
        new MatcherImpl(new NDFACompilerImpl(), RegexpFactory.DEFAULT_REGEXP_FACTORY);
    final AtomicInteger matchCounter = new AtomicInteger();

    for (final String pattern : patterns) {
      matcher.add(pattern, (b, s, e) -> matchCounter.incrementAndGet());
    }

    // Warm-up to stabilize JIT and one-time initialization.
    matcher.match(new StringBuffer(corpus));
    matchCounter.set(0);

    long totalNanos = 0L;
    for (int i = 0; i < iterations; i++) {
      final StringBuffer buffer = new StringBuffer(corpus);
      final long start = System.nanoTime();
      matcher.match(buffer);
      totalNanos += System.nanoTime() - start;
    }

    final int matches = matchCounter.get();
    matcher.shutdown();
    return new MatchRunResult(totalNanos, matches);
  }

  private static String buildCorpus(final List<String> patterns) {
    final StringBuilder sb = new StringBuilder(240_000);
    for (int i = 0; i < 20_000; i++) {
      sb.append("noise-block-").append(i).append(' ');
      if ((i & 63) == 0) {
        sb.append(patterns.get(i % patterns.size())).append(' ');
      }
    }
    return sb.toString();
  }

  private static void restoreProperty(final String key, final String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }

  private record MatchRunResult(long nanos, int matches) {}
}
