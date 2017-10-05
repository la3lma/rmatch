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
 * A harness for running  benchmarks for matcher implementations
 * based on the Wuthering Heights corpus and the set of
 * regular expressions based on words from that corpus.
 */
public final class MatcherBenchmarker {
    /**
     * Our wonderful log.
     */
    private static final Logger LOG =
            Logger.getLogger(MatcherBenchmarker.class.getName());

    /**
     * Utility class.  No public constructor for you!
     */
    private MatcherBenchmarker() {

    }
    /**
     * The location at which we pick up the corpus.
     */
    private static final String REGEXP_LOCATION =
            "corpus/real-words-in-wuthering-heights.txt";
    /**
     * Where to log the results of this test.
     */
    private static final String RESULT_LOG_LOCATION =
            "measurements/handle-the-wuthering-heights-corpus.csv";
    /**
     * We need to remember how many milliseconds there are in a second. ;)
     */
    public static final int MILLISECONDS_IN_A_SECOND = 1000;

    /**
     * Set up the test by inhaling the corpus, enabling the regexpss and
     * running the match.
     *
     * @param noOfRegexpsToAdd The number of regexps to add.
     * @param regexpListLocation, the location of the regexps.
     * @param logLocation the location of the log.
     * @throws RegexpParserException when something goes wrong.
     */
    public static void testACorpus(
            final Buffer b,
            final Matcher matcher,
            final Integer noOfRegexpsToAdd,
            final String regexpListLocation,
            final String logLocation) throws RegexpParserException {
        final long timeAtStart = System.currentTimeMillis();
        LOG.log(Level.INFO, "Doing the thing for {0}", regexpListLocation);
        LOG.log(Level.INFO, "noOfRegexpsToAdd {0}", noOfRegexpsToAdd);
        final FileInhaler fh = new FileInhaler(new File(regexpListLocation));
        final CounterAction wordAction = new CounterAction();
        // XXX ??? wordAction.setVerbose(false);
        // Loop through the regexps, only adding the noOfRegexpsToAdd
        // if that number is non-null, otherwise inhale everything.
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

        matcher.match(b);
        try {
            matcher.shutdown();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        final int finalCount = wordAction.getCounter();
        LOG.log(Level.INFO,
                "Total no of ''word  matches in Wuthering Heights is {0}",
                new Object[]{finalCount});
        final long timeAtEnd = System.currentTimeMillis();
        final long duration = timeAtEnd - timeAtStart;
        LOG.info("Duration was : " + duration + " millis.");
        final Runtime runtime = Runtime.getRuntime();
        final int mb = 1024 * 1024;
        final long usedMemoryInMb =
                (runtime.totalMemory() - runtime.freeMemory()) / mb;
        CSVAppender.append(logLocation, new long[]{
            System.currentTimeMillis() / MILLISECONDS_IN_A_SECOND,
            duration, usedMemoryInMb});
        LOG.log(Level.INFO, "Counter = " + finalCount);
        Counters.dumpCounters();
    }

    public static void testMatcher(
            final Buffer b,
            final Matcher matcher,
            final String[] argv)
            throws RegexpParserException {
        final Integer noOfRegexps;
        if (argv.length != 0) {
            final String noOfRegexpsAsString = argv[0];
            noOfRegexps = Integer.parseInt(noOfRegexpsAsString);
            System.out.println("Benchmarking wuthering heights for index "
                    + noOfRegexps);
        } else {
            noOfRegexps = null;
        }

        testACorpus(b, matcher, noOfRegexps,
                REGEXP_LOCATION, RESULT_LOG_LOCATION);
    }

    public static void testMatcher(
            final Matcher matcher,
            final String[] argv)
            throws RegexpParserException {

        System.out.println("Working Directory = "
                + System.getProperty("user.dir"));

        final Buffer b = new WutheringHeightsBuffer();

        testMatcher(b, matcher, argv);
    }
}
