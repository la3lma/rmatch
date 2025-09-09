package no.rmz.rmatch.performancetests;

import java.util.Arrays;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarkerWithMemory;
import no.rmz.rmatch.performancetests.utils.WutheringHeightsBuffer;

/**
 * Enhanced version of TestJavaRegexpUnfairly that outputs memory consumption data in a format that
 * can be easily captured by shell scripts for JSON output.
 *
 * <p>This benchmark tests the native Java regex engine (java.util.regex.Pattern) against the same
 * corpus and patterns as the rmatch benchmark, allowing for direct performance and memory
 * consumption comparison.
 */
public final class TestJavaRegexpUnfairlyWithMemory {

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
    System.out.println("TestJavaRegexpUnfairlyWithMemory, argx = " + Arrays.toString(argx));

    // Measure memory before creating the matcher
    final Runtime runtime = Runtime.getRuntime();
    final long mb = 1024 * 1024;

    // Force garbage collection to get accurate baseline
    System.gc();
    Thread.yield();
    final long memoryBeforeInMb = (runtime.totalMemory() - runtime.freeMemory()) / mb;

    final Matcher matcher = new JavaRegexpMatcher();
    final Buffer b = new WutheringHeightsBuffer("rmatch-tester/corpus/wuthr10.txt");

    MatcherBenchmarkerWithMemory.testMatcher(b, matcher, argx);

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

    // Kill all threads and get out of here
    System.exit(0);
  }

  /** No public constructor in a utility class. */
  private TestJavaRegexpUnfairlyWithMemory() {}
}
