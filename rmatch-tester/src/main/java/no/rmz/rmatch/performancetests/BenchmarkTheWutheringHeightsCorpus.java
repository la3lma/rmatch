package no.rmz.rmatch.performancetests;

import static java.lang.System.exit;
import static no.rmz.rmatch.impls.MatcherFactory.newMatcher;
import static no.rmz.rmatch.performancetests.utils.MatcherBenchmarker.testMatcher;

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.interfaces.*;

/**
 * A test scenario that will match a bunch of regular expressions against the
 * text of Emily Brontes Wuthering Heights.
 */
public final class BenchmarkTheWutheringHeightsCorpus {

    private final static String DEFAULT_CORPUS_SIZE = "9000";

    /**
     * No public constructor in an utility class.
     */
    private BenchmarkTheWutheringHeightsCorpus() {
    }

    /**
     * The main method.
     *
     * @param argv Command line arguments. If present, arg 1 is interpreted
     *             as a maximum limit on the number of regexps to use.
     * @throws RegexpParserException when things go bad.
     */
    public static void main(final String[] argv) throws RegexpParserException {
        // Get a matcher from the MatcherFactory.


        final String[] argx;
        if (argv == null || argv.length == 0) {
            argx = new String[] { DEFAULT_CORPUS_SIZE };
        } else {
            argx = argv;
        }

        System.out.println("BenchmarkTheWutheringHeightsCorpus, argx = " + argx);
        final Matcher m = newMatcher();
        testMatcher(m, argx);

        // This should normally not be done, since it's slow.  Haven't bothered
        // to add a switch for it yet.
        // GraphDumper.dump(
        //          "benchmarkTheWutheringHeightsCorpus",
        //         m.getNodeStorage());

        System.out.println("Done running BenchmarkTheWutheringHeightsCorpus");
        exit(0);
    }
}
