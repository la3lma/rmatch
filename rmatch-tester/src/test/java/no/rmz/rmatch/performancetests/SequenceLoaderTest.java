package no.rmz.rmatch.performancetests;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherFactory;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.performancetests.utils.CSVAppender;
import no.rmz.rmatch.performancetests.utils.FileInhaler;
import no.rmz.rmatch.performancetests.utils.StringSourceBuffer;
import no.rmz.rmatch.performancetests.utils.WutheringHeightsBuffer;
import no.rmz.rmatch.utils.CounterAction;
import no.rmz.rmatch.utils.Counters;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * This is a kind of extended functionality test, bordering
 * on integration test.  It will read in the Wuthering Heights corpus
 * and run a set of tests against it.  The idea is that this should work
 * both correctly and reasonably efficient and if it doesn't something is
 * broken.  It has turned out to be a very useful test for smoking out
 * various weird bugs that does not necessarily manifest themselves in
 * very small tests.
 */
@RunWith(MockitoJUnitRunner.class)
public class SequenceLoaderTest {

    /**
     * Our Dear Logger.
     */
    private static final Logger LOG =
            Logger.getLogger(SequenceLoaderTest.class.getName());

    /**
     * The location where we can find a file containing
     * some of the words in the Wuthering Heights corpus.
     */
    public static final String LOCATION_OF_SOME_WORDS_FROM_WUTHERING_HEIGHTS =
            "corpus/some-words-in-wuthering-heigths.txt";

    /**
     * The location of a file containing a small number of
     * words extracted from Wuthering Heights.
     */
    public static final String VERY_FEW_WORDS_FROM_WUTHERING_HEIGHTS =
            "corpus/very-few-words-in-wuthering-heigths.txt";

    /**
     * The location where the test results of the test looking for the
     * words in the "..VERY_FEW_WORDS..." list of words will be appended.
     */
    public static final String THE_LOCATION_OF_TEST_RESULTS_FOR_VERY_FEW_WORDS =
            "measurements/testWutheringHeightsCorpusWithVeryFewRegexps.csv";

    /**
     * The location where the test results of the test looking for the
     * words in the "..SOME_WORDS..." list of words will be appended.
     */
    public static final String MEASUREMENT_RESULTS_FROM_SOME_WORDS_TESTS =
            "measurements/testWutheringHeightsCorpusWithSomeRegexps.csv";

    /**
     * Location of the Wuthering Heights text.
     */
    public static final String LOCATION_OF_WUTHERING_HEIGHTS_WORDS =
            "corpus/words-in-wuthering-heigths.txt";

    /**
     * A mocked action. Used for verifying that matches were found.
     */
    @Mock
    Action action;


    /**
     * A constant that needs to be final and globally accessible within
     * the class.
     */
    private static final  String FROST_STRING = "frost";

    /**
     *  Test article, an implementation of the Matcher interface.
     */
    private Matcher m;


    /**
     * The set of Regexp/NDFAnodes that will be what the
     * mocked-up compiler returns.
     */
    private Map<Regexp, NDFANode> compilationResults;


    /**
     * Set up the compilationResults map to hold reasonable
     * values, make a new MockerCompiler instance that
     * will return a mocked up compiler instance.
     */
    @Before
    public final void setUp() {
        m = MatcherFactory.newMatcher();
    }

    /**
     * XXX This test fails, review and report back.
     * @throws RegexpParserExecption when syntax errors occur.
     */
    @Ignore
    @Test
    public final void testMockedVerylongMatchSequences()
            throws RegexpParserException {

        // Set up parameters
        final int noOfPatterns = 600;
        final String pattern = FROST_STRING;
        final int startIndexInPattern = 0;
        final int endIndexInPattern = 4;
        final int lengthOfPattern = pattern.length();


        // Build t
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < noOfPatterns; i++) {
            sb.append(pattern);
        }
        final String bufferString = sb.toString();
        final StringSourceBuffer b =
                new  StringSourceBuffer(bufferString);
        m.add(FROST_STRING, action);

        m.match(b);

        for (int i = 0; i < noOfPatterns; i++) {
            final int offset = lengthOfPattern * i;
            final int start = offset + startIndexInPattern;
            final int end = offset + endIndexInPattern;
            verify(action).performMatch(b, start, end);
        }
    }

    /**
     * Well, I guess this is pretty much self explanatory.
     */
    private static final int
            ARBITRARY_REASONABLE_LOWER_LIMIT_FOR_NO_OF_FINAL_COUNT = 5;


    /**
     * Run through the entire wuthering heights corpus and look
     * for instandes of "horse" and "frost".
     * @throws RegexpParserExecption when syntax errors occur.
     */
    @Test
    public final void testWutheringHeightsCorpus()
            throws RegexpParserException {

        final Buffer b = new WutheringHeightsBuffer();
        final CounterAction frostCounter = new CounterAction();
        final CounterAction horseCounter = new CounterAction();


        m.add(FROST_STRING, frostCounter);
        m.add("horse", horseCounter);
        m.match(b);

        final int finalCount = frostCounter.getCounter();
        LOG.info("Total no of 'frost' matches in Wuthering Heights is "
                + finalCount);
        LOG.info("Total no of 'horse' matches in Wuthering Heights is "
                + horseCounter.getCounter());
        assertTrue(finalCount > ARBITRARY_REASONABLE_LOWER_LIMIT_FOR_NO_OF_FINAL_COUNT);

        Counters.dumpCounters();
    }

    /**
     * The number of milliseonds in a seond.
     */
    private static final int MILLIS_IN_SECOND = 1000;

    /**
     * The maximum permitted time to use when running through
     * the wuthering height corpus for two regexps.
     */
    private static final int MAX_TIME_TO_USE_IN_MILLIS = 5000;

    /**
     * The maximum amount of memory permitted used.
     */
    private static final int MAX_MEMORY_TO_USE_IN_MB = 2000;

    /**
     * Check with only a very few regexps.
     * @throws RegexpParserExecption when syntax errors occur.
     */
    @Test
    public final void testWutheringHeightsCorpusWithVeryFewRegexps()
            throws RegexpParserException {
        final CorpusTestResult result =
                testWithRegexpsFromFile(VERY_FEW_WORDS_FROM_WUTHERING_HEIGHTS);
        // XXX The number of matches needs to be verified.  The number
        //     we use now is fundamentally bogus!
        assertTrue(result.getDuration() < MAX_TIME_TO_USE_IN_MILLIS);
        System.out.println("Used this much memory: " + result.getMaxNoOfMbsUsed());
        assertTrue(result.getMaxNoOfMbsUsed() < MAX_MEMORY_TO_USE_IN_MB);

        CSVAppender.append(
                THE_LOCATION_OF_TEST_RESULTS_FOR_VERY_FEW_WORDS,
                new long[]{System.currentTimeMillis() / MILLIS_IN_SECOND,
                    result.getDuration(), result.getMaxNoOfMbsUsed()});
        Counters.dumpCounters();
    }

    /**
     * This is the number of mathes we expect to see when looking for
     * matches using the "some-words-in..." list of words to look for.
     */
    private static final int NO_OF_OBSERVED_MATCHES_IN_SOME_REGEXP_TEST =
            2080;

    /**
     * Check with a few more regexps.
     * @throws RegexpParserExecption when syntax errors occur.
     */
    @Test
    public void testWutheringHeightsCorpusWithSomeRegexps()
            throws RegexpParserException {
        final CorpusTestResult result =
         testWithRegexpsFromFile(LOCATION_OF_SOME_WORDS_FROM_WUTHERING_HEIGHTS);
        // XXX The number of matches needs to be verified.  The number
        //     we use now is fundamentally bogus!
        assertTrue(result.getNoOfMatches()
                == NO_OF_OBSERVED_MATCHES_IN_SOME_REGEXP_TEST);
        assertTrue("result was = " + result, result.getDuration() < MAX_TIME_TO_USE_IN_MILLIS);
        assertTrue("result was = " + result, result.getMaxNoOfMbsUsed() < MAX_MEMORY_TO_USE_IN_MB);

        CSVAppender.append(
                MEASUREMENT_RESULTS_FROM_SOME_WORDS_TESTS,
                new long[]{System.currentTimeMillis() / MILLIS_IN_SECOND,
                    result.getDuration(),
                    result.getMaxNoOfMbsUsed()});
    }

    /**
     * Test with a regexp for just about every word occuring in
     * the corpus.
     *
     * @throws RegexpParserExecption when syntax errors occur.
     */
    @Ignore
    @Test
    public void testWutheringHeightsCorpusWithAWholeLotOfRegexps()
            throws RegexpParserException {
        testWithRegexpsFromFile(LOCATION_OF_WUTHERING_HEIGHTS_WORDS);
    }

    /**
     * Test the corpus using regexps from a file.
     * @param regexpFile a file with a regexp on each line.
     * @return A summary of the test run including performance stats.
     */
    private CorpusTestResult testWithRegexpsFromFile(final String regexpFile)
            throws RegexpParserException {
        final long timeAtStart = System.currentTimeMillis();
        LOG.info("Checking matches in file " + regexpFile);
        final FileInhaler fh = new FileInhaler(new File(regexpFile));
        final CounterAction allWordsAction = new CounterAction();
        long noOfThingsToLookFor = 0;

        final SortedMap<String, CounterAction> wordCounters =
                new TreeMap<String, CounterAction>();


        for (final String word : fh.inhaleAsListOfLines()) {
            final CounterAction wordAction = new CounterAction();
            wordCounters.put(word, wordAction);
            m.add(word, allWordsAction);
            m.add(word, wordAction);
            noOfThingsToLookFor++;
        }

        LOG.info("no of words to look for: " + noOfThingsToLookFor);

        final Buffer b = new WutheringHeightsBuffer();
        m.match(b);

        LOG.info("No of characters read:  "
                + b.getCurrentPos()
                + " chars from "
                + regexpFile);
        final int noOfMatches = allWordsAction.getCounter();
        LOG.log(Level.INFO,
                "Total no of ''word''  matches in Wuthering Heights is {0}",
                new Object[]{noOfMatches});
        final long timeAtEnd = System.currentTimeMillis();
        final long duration = (timeAtEnd - timeAtStart);
        LOG.info("Duration was : " + duration + " millis.");


        final Runtime runtime = Runtime.getRuntime();
        final int mb = 1024 * 1024;

        final long usedMemoryInMb =
                ((runtime.totalMemory() - runtime.freeMemory()) / mb);


        final CorpusTestResult result = new CorpusTestResult(
                regexpFile,
                duration,
                allWordsAction.getCounter(),
                noOfThingsToLookFor,
                usedMemoryInMb);

        // Checking for some consistency in the results and complaining loudly
        // if it isn't there, then fail. ;)
        if (result.getNoOfMatches() < result.getNoOfWordsToLookFor()) {
            for (final Entry<String, CounterAction> entry
                    : wordCounters.entrySet()) {
                final int count = entry.getValue().getCounter();
                if (count > 0) {
                    System.out.printf("%s:%d\n", entry.getKey(), count);
                }
            }
        }

        assertTrue("Not enough matches, got only " + result.getNoOfMatches()
                + " but expected at least "
                + result.getNoOfWordsToLookFor() + ".",
                result.getNoOfMatches() > result.getNoOfWordsToLookFor());

        return result;
    }
}
