package no.rmz.rmatch.performancetests;

import java.util.Arrays;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.*;
import no.rmz.rmatch.performancetests.utils.MatcherBenchmarker;

/**
 * A test scenario that will match a bunch of regular expressions against the
 * text of Emily Brontes Wuthering Heights.
 */
public final class BenchmarkTheWutheringHeightsCorpus {

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
            argx = new String[]{"10000"};
        } else {
            argx = argv;
        }
        System.out.println("BenchmarkTheWutheringHeightsCorpus, argx = " + Arrays.toString(argx));
        final Matcher m = MatcherFactory.newMatcher();
        MatcherBenchmarker.testMatcher(m, argx);

        // This should normally not be done, since it's slow.  Haven't bothered
        // to add a switch for it yet.
        // GraphDumper.dump(
        //          "benchmarkTheWutheringHeightsCorpus",
        //         m.getNodeStorage());
        System.exit(0);  // XXX In case of dangling threads
    }

    /**
     * No public constructor in an utility class.
     */
    private BenchmarkTheWutheringHeightsCorpus() {
    }

}
