package no.rmz.rmatch.performancetests.utils;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.utils.CounterAction;
import no.rmz.rmatch.utils.Counters;

/**
 * Enhanced version of MatcherBenchmarker that provides detailed memory consumption information
 * suitable for capture by shell scripts and inclusion in JSON benchmark results.
 */
public final class MatcherBenchmarkerWithMemory {
  /** Our wonderful log. */
  private static final Logger LOG = Logger.getLogger(MatcherBenchmarkerWithMemory.class.getName());

  /** Utility class. No public constructor for you! */
  private MatcherBenchmarkerWithMemory() {}

  /** The location at which we pick up the corpus. */
  private static final String REGEXP_LOCATION =
      "rmatch-tester/corpus/real-words-in-wuthering-heights.txt";

  /** Where to log the results of this test. */
  private static final String RESULT_LOG_LOCATION =
      "rmatch-tester/measurements/handle-the-wuthering-heights-corpus.csv";

  /**
   * Enhanced version of testACorpus that provides detailed memory tracking.
   *
   * @param b The buffer containing the corpus
   * @param matcher The matcher to test
   * @param noOfRegexpsToAdd The number of regexps to add
   * @param regexpListLocation The location of the regexps
   * @param logLocation The location of the log
   * @throws RegexpParserException when something goes wrong
   */
  public static void testACorpusWithMemory(
      final Buffer b,
      final Matcher matcher,
      final Integer noOfRegexpsToAdd,
      final String regexpListLocation,
      final String logLocation)
      throws RegexpParserException {

    final long timeAtStart = System.currentTimeMillis();
    LOG.log(Level.INFO, "Doing the thing for {0}", regexpListLocation);
    LOG.log(Level.INFO, "noOfRegexpsToAdd {0}", noOfRegexpsToAdd);

    final Runtime runtime = Runtime.getRuntime();
    final int mb = 1024 * 1024;

    // Memory measurement at start of pattern loading
    System.gc();
    Thread.yield();
    final long memoryBeforePatterns = (runtime.totalMemory() - runtime.freeMemory()) / mb;

    final FileInhaler fh = new FileInhaler(new File(regexpListLocation));
    final CounterAction wordAction = new CounterAction();

    // Loop through the regexps, only adding the noOfRegexpsToAdd
    int counter;
    if (noOfRegexpsToAdd != null && noOfRegexpsToAdd > 0) {
      counter = noOfRegexpsToAdd;
    } else {
      counter = -1;
    }

    for (final String word : fh.inhaleAsListOfLines()) {
      if (counter == 0) {
        break;
      }
      matcher.add(word, wordAction);
      if (counter > 0) {
        counter -= 1;
      }
    }

    LOG.log(Level.INFO, "(regexp) counter {0}", counter);

    // Memory measurement after pattern loading, before matching
    System.gc();
    Thread.yield();
    final long memoryAfterPatterns = (runtime.totalMemory() - runtime.freeMemory()) / mb;

    // Run the actual matching
    matcher.match(b);

    // Memory measurement after matching, before shutdown
    System.gc();
    Thread.yield();
    final long memoryAfterMatching = (runtime.totalMemory() - runtime.freeMemory()) / mb;

    try {
      matcher.shutdown();
    } catch (InterruptedException ex) {
      throw new RuntimeException(ex);
    }

    // Final memory measurement
    System.gc();
    Thread.yield();
    final long memoryAfterShutdown = (runtime.totalMemory() - runtime.freeMemory()) / mb;

    final int finalCount = wordAction.getCounter();
    LOG.log(
        Level.INFO,
        "Total no of word  matches in Wuthering Heights is {0}",
        new Object[] {finalCount});

    final long timeAtEnd = System.currentTimeMillis();
    final long duration = timeAtEnd - timeAtStart;
    LOG.info("Duration was : " + duration + " millis.");

    // Output detailed memory information
    final long totalMemoryInMb = runtime.totalMemory() / mb;
    final long maxMemoryInMb = runtime.maxMemory() / mb;

    System.out.println("DETAILED_MEMORY_STATS_BEGIN");
    System.out.println("memory_before_patterns_mb=" + memoryBeforePatterns);
    System.out.println("memory_after_patterns_mb=" + memoryAfterPatterns);
    System.out.println("memory_after_matching_mb=" + memoryAfterMatching);
    System.out.println("memory_after_shutdown_mb=" + memoryAfterShutdown);
    System.out.println("memory_pattern_loading_mb=" + (memoryAfterPatterns - memoryBeforePatterns));
    System.out.println("memory_matching_mb=" + (memoryAfterMatching - memoryAfterPatterns));
    System.out.println("memory_peak_used_mb=" + Math.max(memoryAfterPatterns, memoryAfterMatching));
    System.out.println("memory_total_mb=" + totalMemoryInMb);
    System.out.println("memory_max_mb=" + maxMemoryInMb);
    System.out.println("duration_ms=" + duration);
    System.out.println("match_count=" + finalCount);
    System.out.println("DETAILED_MEMORY_STATS_END");

    // Also do the original CSV logging for compatibility
    final long usedMemoryInMb = memoryAfterMatching;
    CSVAppender.append(
        logLocation, new long[] {System.currentTimeMillis() / 1000, duration, usedMemoryInMb});

    LOG.log(Level.INFO, "Counter = " + finalCount);
    Counters.dumpCounters();
  }

  public static void testMatcher(final Buffer b, final Matcher matcher, final String[] argv)
      throws RegexpParserException {
    final Integer noOfRegexps;
    if (argv.length != 0) {
      final String noOfRegexpsAsString = argv[0];
      noOfRegexps = Integer.parseInt(noOfRegexpsAsString);
      System.out.println("Benchmarking wuthering heights for index " + noOfRegexps);
    } else {
      noOfRegexps = null;
    }

    testACorpusWithMemory(b, matcher, noOfRegexps, REGEXP_LOCATION, RESULT_LOG_LOCATION);
  }

  public static void testMatcher(final Matcher matcher, final String[] argv, String pathToCorpus)
      throws RegexpParserException {

    final Buffer b = new WutheringHeightsBuffer(pathToCorpus);
    testMatcher(b, matcher, argv);
  }
}
