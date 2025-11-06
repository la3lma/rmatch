package no.rmz.rmatch.benchmarks.framework;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.utils.CounterAction;
import no.rmz.rmatch.utils.StringBuffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Lightweight functional verification benchmarks for small datasets.
 *
 * <p>This class runs basic functional tests to ensure small corpus sizes work correctly, but
 * focuses on verification rather than performance measurement. These are treated as "ramp-up" tests
 * to verify functionality before running the main performance benchmarks.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(
    value = 1,
    jvmArgs = {"-Xms1G", "-Xmx1G"})
@Warmup(iterations = 1, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
public class FunctionalVerificationBench {

  private static final Logger LOG = Logger.getLogger(FunctionalVerificationBench.class.getName());

  @Param({"10"})
  public int patternCount;

  @Param({"VERY_FEW", "SOME"})
  public String corpusSize;

  @Param({"WUTHERING_HEIGHTS"})
  public String textCorpus;

  private String corpusText;
  private List<String> corpusPatterns;

  @Setup(Level.Trial)
  public void setup() {
    LOG.info("Setting up FunctionalVerificationBench with " + patternCount + " patterns");

    // Load corpus data for functional verification
    loadCorpusData();

    LOG.info("Functional verification setup complete");
  }

  private void loadCorpusData() {
    try {
      // Load text corpus
      CorpusInputProvider.TextCorpus corpus = CorpusInputProvider.TextCorpus.valueOf(textCorpus);
      corpusText = CorpusInputProvider.loadTextCorpus(corpus);
      LOG.info(
          "Loaded corpus text for verification: "
              + corpus.getFilename()
              + " ("
              + corpusText.length()
              + " chars)");

      // Load regex patterns
      CorpusInputProvider.CorpusSize size = CorpusInputProvider.CorpusSize.valueOf(corpusSize);
      corpusPatterns = CorpusInputProvider.loadRegexPatterns(size);
      LOG.info(
          "Loaded corpus patterns for verification: "
              + size.getFilename()
              + " ("
              + corpusPatterns.size()
              + " patterns)");
    } catch (IOException e) {
      LOG.warning("Failed to load corpus data for verification: " + e.getMessage());
      // Fall back to minimal synthetic data for verification
      corpusText = "the quick brown fox jumps over the lazy dog";
      corpusPatterns = List.of("the", "fox", "dog");
    }
  }

  /**
   * Functional verification benchmark using small corpus datasets.
   *
   * <p>This benchmark verifies that small corpus sizes work correctly without focusing on
   * performance measurement. It's designed to be a quick functional check.
   *
   * @param bh JMH blackhole for result consumption
   * @return Test results for functional verification
   */
  @Benchmark
  public TestResults functionalVerification(final Blackhole bh) {
    LOG.info(
        "Running functional verification with corpusSize="
            + corpusSize
            + ", textCorpus="
            + textCorpus
            + ", patternCount="
            + patternCount);

    final long startTime = System.nanoTime();
    final Runtime runtime = Runtime.getRuntime();
    final long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

    try {
      final Matcher matcher = MatcherFactory.newMatcher();
      final CounterAction action = new CounterAction();

      // Use patterns from corpus, limited by patternCount
      final int actualPatternCount = Math.min(patternCount, corpusPatterns.size());
      for (int i = 0; i < actualPatternCount; i++) {
        matcher.add(corpusPatterns.get(i), action);
      }

      // Perform matching against corpus text
      final StringBuffer buffer = new StringBuffer(corpusText);
      matcher.match(buffer);
      matcher.shutdown();

      // Calculate basic metrics for verification
      final long endTime = System.nanoTime();
      final long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
      final long duration = endTime - startTime;
      final long memoryUsed = Math.max(0, memoryAfter - memoryBefore);

      final int matchCount = action.getCounter();
      final double throughput = (double) matchCount / TimeUnit.NANOSECONDS.toSeconds(duration);

      final TestResults results =
          new TestResults(
              "functional_verification_" + corpusSize.toLowerCase(),
              duration,
              memoryUsed,
              matchCount,
              actualPatternCount,
              throughput);

      bh.consume(results);
      return results;

    } catch (final Exception e) {
      LOG.severe("Functional verification failed: " + e.getMessage());
      throw new RuntimeException("Functional verification failed", e);
    }
  }
}
