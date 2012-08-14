package no.rmz.rmatch.performancetests;

import java.util.logging.Logger;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.impls.RegexpImpl;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.NDFACompiler;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.interfaces.RegexpStorage;
import no.rmz.rmatch.mockedcompiler.CharPlusNode;
import no.rmz.rmatch.utils.CounterAction;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Matchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * This is a test that checks that the regular expression "a+" can be run, and
 * eventually also compiled correctly.
 *
 *
 * This set of tests assumes that the basic mechanics works, but will stress the
 * implementation by running larg(ish) tests.
 */
@RunWith(MockitoJUnitRunner.class)
public class APlusLoader {

    /**
     * Our Dear Logger.
     */
    private static final Logger LOG =
            Logger.getLogger(APlusLoader.class.getName());

    /**
     * If we don't get at least fire matches, something is
     * wrong.
     */
    private static final int
            REASONABLE_MINIMUM_GUESS_OF_ASTARS_IN_WUTHERING_HEIGTHS = 10;

    /**
     * Mocke action, used to count matches.
     */
    @Mock
    Action action;


    /**
     * Mocked compiler used to inject precooked NDFA at appropriate
     * spot.
     */
    @Mock
    NDFACompiler compiler;


    /**
     * The string "a+".
     */
    private String aPlusString;

    /**
     * Test item, a matcher.
     */
    private Matcher m;

    /**
     * Test item, a regexp for "a+".
     */
    private Regexp regexp;

    /**
     * Set up test items and context.
     */
    @Before
    public void setUp() throws RegexpParserException {
        final String finalAPlus = "a+";
        aPlusString = finalAPlus;
        regexp = new RegexpImpl(aPlusString);
        final NDFANode aplus =
                new CharPlusNode(Character.valueOf('a'), regexp, true);

        when(compiler.compile((Regexp) any(),
                (RegexpStorage) any())).thenReturn(aplus);

        final RegexpFactory regexpFactory = new RegexpFactory() {
            @Override
            public Regexp newRegexp(final String regexpString) {
                if (regexpString.equals(finalAPlus)) {
                    return regexp;
                } else {
                    return RegexpFactory.DEFAULT_REGEXP_FACTORY
                            .newRegexp(regexpString);
                }
            }
        };

        m = new MatcherImpl(compiler, regexpFactory);
    }


    /**
     * Look for the pattern in a very long sequence where there will
     * be many matches.
     */
    @Test
    public void testMockedVerylongMatchSequences() throws RegexpParserException {

        // Set up parameters
        final int noOfPatterns = 600;
        final String pattern = "ab";
        final int startIndexInPattern = 0;
        final int endIndexInPattern = 0;
        final int lengthOfPattern = pattern.length();


        // Build t
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < noOfPatterns; i++) {
            sb.append(pattern);
        }
        final String bufferString = sb.toString();
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils
                .StringBuffer(bufferString);
        m.add(aPlusString, action);

        m.match(b);

        for (int i = 0; i < noOfPatterns; i++) {
            final int offset = lengthOfPattern * i;
            verify(action).performMatch(b,
                    offset + startIndexInPattern, offset + endIndexInPattern);
        }
    }

    /**
     * Look for matches in the wuthering heights corpus.
     */
    @Test
    public void testWutheringHeightsCorpus() throws RegexpParserException {

        final Buffer b = new WutheringHeightsBuffer();
        final CounterAction counterAction = new CounterAction();

        m.add(aPlusString, counterAction);
        m.match(b);

        final int finalCount = counterAction.getCounter();
        LOG.info("Total no of 'a*' matches in Wuthering Heights is "
                + finalCount);
        assertTrue(finalCount
                > REASONABLE_MINIMUM_GUESS_OF_ASTARS_IN_WUTHERING_HEIGTHS);
    }
}
