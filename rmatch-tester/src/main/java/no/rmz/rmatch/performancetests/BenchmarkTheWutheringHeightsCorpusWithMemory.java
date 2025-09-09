package no.rmz.rmatch.performancetests;

import static no.rmz.rmatch.performancetests.utils.WutheringHeightsBuffer.LOCATION_OF_WUTHERING_HEIGHTS;

import java.util.Arrays;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarkerWithMemory;

/**
 * Enhanced version of BenchmarkTheWutheringHeightsCorpus that outputs memory consumption data in a
 * format that can be easily captured by the shell script for JSON output.
 */
public final class BenchmarkTheWutheringHeightsCorpusWithMemory {

  /**
   * The main method.
   *
   * @param argv Command line arguments. If present, arg 1 is interpreted as a maximum limit on the
   *     number of regexps to use.
   * @throws RegexpParserException when things go bad.
   */
  public static void main(final String[] argv) throws RegexpParserException {
    final String[] argx;
    if (argv == null || argv.length == 0) {
      argx = new String[] {"10000"};
    } else {
      argx = argv;
    }
    System.out.println("BenchmarkTheWutheringHeightsCorpus, argx = " + Arrays.toString(argx));

    // Measure memory before creating the matcher
    final Runtime runtime = Runtime.getRuntime();
    final long mb = 1024 * 1024;

    // Force garbage collection to get accurate baseline
    System.gc();
    Thread.yield();
    final long memoryBeforeInMb = (runtime.totalMemory() - runtime.freeMemory()) / mb;

    final Matcher m = MatcherFactory.newMatcher();
    MatcherBenchmarkerWithMemory.testMatcher(
        m, argx, "rmatch-tester/" + LOCATION_OF_WUTHERING_HEIGHTS);

    // Force garbage collection and measure final memory
    System.gc();
    Thread.yield();
    final long memoryAfterInMb = (runtime.totalMemory() - runtime.freeMemory()) / mb;
    final long totalMemoryInMb = runtime.totalMemory() / mb;
    final long maxMemoryInMb = runtime.maxMemory() / mb;

    // Output memory statistics in a format the shell script can parse
    System.out.println("MEMORY_STATS_BEGIN");
    System.out.println("memory_before_mb=" + memoryBeforeInMb);
    System.out.println("memory_after_mb=" + memoryAfterInMb);
    System.out.println("memory_used_mb=" + (memoryAfterInMb - memoryBeforeInMb));
    System.out.println("memory_total_mb=" + totalMemoryInMb);
    System.out.println("memory_max_mb=" + maxMemoryInMb);
    System.out.println("MEMORY_STATS_END");

    System.exit(0); // XXX In case of dangling threads
  }

  /** No public constructor in an utility class. */
  private BenchmarkTheWutheringHeightsCorpusWithMemory() {}
}
