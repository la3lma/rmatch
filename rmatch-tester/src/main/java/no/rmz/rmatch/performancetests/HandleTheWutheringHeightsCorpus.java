package no.rmz.rmatch.performancetests;

import java.io.File;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.impls.MultiMatcher;
import no.rmz.rmatch.impls.RegexpImpl;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.mockedcompiler.CharSequenceCompiler;
import no.rmz.rmatch.mockedcompiler.MockerCompiler;
import no.rmz.rmatch.utils.CounterAction;
import no.rmz.rmatch.utils.Counters;

/**
 * A test scenario that will match a bunch of regular expressions
 * against the text of Emily Brontes Wuthering Heights.
 */
public final class HandleTheWutheringHeightsCorpus {

    /**
     * Our wonderful log.
     */
    private static final Logger LOG =
            Logger.getLogger(HandleTheWutheringHeightsCorpus.class.getName());

    /**
     * We need to remember how many milliseconds there are in
     * a second. ;)
     */
    public static final int MILLISECONDS_IN_A_SECOND = 1000;


    /**
     * No public constructor in an utility class.
     */
    private HandleTheWutheringHeightsCorpus() {
    }


    /**
     * Set up the test by inhaling the corpus, enabling the
     * regexpss and running the match.
     * @param filename the location of the corpus.
     */
    public static void testACorpus(final String filename) throws RegexpParserException {

        /**
         * The mocked compilation results.
         */
        final HashMap<Regexp, NDFANode> compilationResults =
                new HashMap<Regexp, NDFANode>();

   
      


        final Matcher m = MatcherFactory.newMatcher();
                
        final long timeAtStart = System.currentTimeMillis();
        
        LOG.info("Doing the thing for " + filename);
        
        final FileInhaler fh = new FileInhaler(new File(filename));
        final CounterAction wordAction = new CounterAction();
        for (final String word : fh.inhaleAsListOfLines()) {
            m.add(word, wordAction);
        }

        final Buffer b = new WutheringHeightsBuffer();
        m.match(b);
        try {
            m.shutdown();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        final int finalCount = wordAction.getCounter();
        LOG.log(Level.INFO,
                "Total no of ''word  matches in Wuthering Heights is {0}",
                new Object[]{finalCount});
        final long timeAtEnd = System.currentTimeMillis();
        final long duration = (timeAtEnd - timeAtStart);
        LOG.info("Duration was : " + duration + " millis.");


        final Runtime runtime = Runtime.getRuntime();
        final int mb = 1024 * 1024;

        final long usedMemoryInMb =
                ((runtime.totalMemory() - runtime.freeMemory()) / mb);

        CSVAppender.append(
                "measurements/handle-the-wuthering-heights-corpus.csv",
                new long[]{System.currentTimeMillis()
                    / MILLISECONDS_IN_A_SECOND, duration, usedMemoryInMb});
        Counters.dumpCounters();
    }


    

    /**
     * The main method.
     * @param argv Command line arguments. All of which are ignored.
     */
    public static void main(final String[] argv) throws RegexpParserException {
        testACorpus("corpus/real-words-in-wuthering-heights.txt");
    }
}
