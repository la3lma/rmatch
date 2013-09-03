package no.rmz.rmatch.performancetests;

import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherFactory;

/**
 * A test scenario that will match a bunch of regular expressions against the
 * text of Emily Brontes Wuthering Heights.
 */
public final class BenchmarkTheWutheringHeightsCorpus {

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
            argx = new String[] {"10000"};
        } else {
            argx = argv;
        }

        System.out.println("BenchmarkTheWutheringHeightsCorpus, argx = " + argx);
        MatcherBenchmarker.testMatcher(MatcherFactory.newMatcher(), argx);
        System.out.println("Done running BenchmarkTheWutheringHeightsCorpus");
        System.exit(0);
    }
}