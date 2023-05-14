package no.rmz.rmatch.performancetests.utils;

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.utils.CounterAction;
import no.rmz.rmatch.utils.Counters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            "rmatch-tester/corpus/real-words-in-wuthering-heights.txt";
    /**
     * Where to log the results of this test.
     */
    private static final String RESULT_LOG_LOCATION =
            "rmatch-tester/measurements/handle-the-wuthering-heights-corpus.csv";
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
                "Total no of word  matches in Wuthering Heights is {0}",
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


    public record  LoggedMatch(String matcherTypeName, String regex, int start, int end){}

    public record TestRunResult(String matcherTypeName, Collection<LoggedMatch> loggedMatches, long usedMemoryInMb, long durationInMillis){};

    public record TestPairSummary(
            long timestamp,
            String testSeriesId,
            String matcherTypeName1, long usedMemoryInMb1, long durationInMillis1,
            String matcherTypeName2, long usedMemoryInMb2, long durationInMillis2,
            int noOfMatches,
            int noOfMismatches,
            int noOfRegexps,
            int corpusLength
    ){}


    public static void writeSummaryToFile(String filePath, TestPairSummary summary) {
        File csvOutputFile = new File(filePath);
        boolean writeHeader = !csvOutputFile.exists();
        try (FileWriter fw = new FileWriter(csvOutputFile, !writeHeader)){
            PrintWriter pw = new PrintWriter(fw);
            if (writeHeader) {
                pw.println("timestamp," +
                        "testSeriesId," +
                        "matcherTypeName1,usedMemoryInMb1,durationInMillis1," +
                        "matcherTypeName2,usedMemoryInMb2,durationInMillis2," +
                        "noOfMatches," +
                        "noOfMismatches," +
                        "noOfRegexps," +
                        "corpusLength");
            }

            pw.println(convertToCsv(summary));
        } catch (IOException e) {
            System.err.println("Could not open CSV file '"+filePath+"'");
        }
    }

    private static String escapeSpecialCharacters(String data) {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    private static String convertToCsv(TestPairSummary summary) {
        String[] data = new String[] {
                Long.toString(summary.timestamp()),
                summary.testSeriesId(),
                summary.matcherTypeName1(),
                Long.toString(summary.usedMemoryInMb1()),
                Long.toString(summary.durationInMillis1()),
                summary.matcherTypeName2(),
                Long.toString(summary.usedMemoryInMb2()),
                Long.toString(summary.durationInMillis2()),
                Integer.toString(summary.noOfMatches()),
                Integer.toString(summary.noOfMismatches()),
                Integer.toString(summary.corpusLength())
        };
        return Stream.of(data)
                .map(c -> escapeSpecialCharacters(c))
                .collect(Collectors.joining(","));
    }


    public static TestRunResult testACorpusNG(
            final String matcherTypeName,
            final Matcher matcher,
            final List<String> allRegexps,
            final Buffer buf) {

        Object guard = new Object();

        Comparator<LoggedMatch> matchComparator = new Comparator<MatcherBenchmarker.LoggedMatch>() {
            @Override
            public int compare(MatcherBenchmarker.LoggedMatch o1, MatcherBenchmarker.LoggedMatch o2) {
                if (o1 == o2) {
                    return 0;
                }
                if (o1 == null) {
                    return -1;
                }
                if (o2 == null) {
                    return 1;
                }
                int result = Integer.compare(o1.start(), o2.start());
                if (result != 0) {
                    return result;
                }
                result = o1.regex().compareTo(o2.regex());
                if (result != 0) {
                    return result;
                }
                return Integer.compare(o1.end(), o2.end());
            }
        };

        final Collection<LoggedMatch> loggedMatches = new TreeSet<LoggedMatch>(matchComparator);

        for (String regex: allRegexps) {
            Action action = new Action() {
                @Override
                public void performMatch(Buffer b, int start, int end) {
                    LoggedMatch ob = new LoggedMatch(matcherTypeName, regex, start, end);
                    synchronized (guard) {
                        loggedMatches.add(ob);
                    }
                }
            };
            try {
                matcher.add(regex, action);
            } catch (RegexpParserException e) {
                System.err.println("Could not add action for regex " + regex);
                System.exit(1);
            }
        }

        // Run the matches
        final long timeAtStart = System.currentTimeMillis();
        matcher.match(buf);
        try {
            matcher.shutdown();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        final long timeAtEnd = System.currentTimeMillis();
        final long duration = timeAtEnd - timeAtStart;

        LOG.info("Duration was : " + duration + " millis.");
        final Runtime runtime = Runtime.getRuntime();
        final int mb = 1024 * 1024;
        final long usedMemoryInMb =
                (runtime.totalMemory() - runtime.freeMemory()) / mb;
        LOG.log(Level.INFO, "usedMemoryInMb = " + usedMemoryInMb);

        return new TestRunResult(matcherTypeName, loggedMatches, usedMemoryInMb, duration);
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
            final String[] argv,
            String pathToCorpus)
            throws RegexpParserException {

        final Buffer b = new WutheringHeightsBuffer(pathToCorpus);

        testMatcher(b, matcher, argv);
    }
}
