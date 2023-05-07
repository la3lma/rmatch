/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.integrationtests;

import no.rmz.rmatch.compiler.CharNode;
import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.compiler.TerminalNode;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.impls.RegexpImpl;
import no.rmz.rmatch.interfaces.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This is a test that checks that the regular expression "a" can be run.
 * If this doesn't work, then nothing will.
 */
@ExtendWith(MockitoExtension.class)
public final class ATest {


    /**
     * Mocked action, used to check for matches.
     */
    @Mock
    Action action;

    /**
     * Mocked compiler, used to inject precooked NDFA at appropriate
     * places.
     */
    @Mock
    NDFACompiler compiler;

    /**
     * A string:  "a".
     */
    private String aString;

    /**
     * Test item, a matcher.
     */
    private Matcher m;

    /**
     * Test item, a regexp.
     */
    private Regexp regexp;

    /**
     * Set up test items and a mocked context.
     */
    @BeforeEach
    public void setUp() throws RegexpParserException {
        final String finalA = "a";
        aString = finalA;
        regexp = new RegexpImpl(aString);
        final NDFANode aNode = new CharNode(
                new TerminalNode(regexp),
                'a', regexp);

        when(compiler.compile((Regexp) any(),
                (RegexpStorage) any())).thenReturn(aNode);

        final RegexpFactory regexpFactory = new RegexpFactory() {
            @Override
            public Regexp newRegexp(final String regexpString) {
                if (regexpString.equals(finalA)) {
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
     * This is kind of a tripwire test. It was written since no actions were run
     * i testMockedMatch.
     */
    @Test
    public void testActionTransferToRegexpThroughRegexpStorage() throws RegexpParserException {
        assertTrue(!regexp.hasActions(),
                "the regexp should not initially have actions");
        m.add(aString, action);
        assertTrue(regexp.hasActions(), "the regexp should have actions");
    }


    /**
     * Look for a match terminated by the character  "b".
     */
    @Test
    public void testBterminated() throws RegexpParserException {
        final String is = "ab";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aString, action);

        m.match(b);

        // Starting out accepting any kind of match
        verify(action).performMatch(b, 0, 0);
    }

    /**
     * Look for a match terminated by end of string.
     */
    @Test
    public void testEOSterminated() throws RegexpParserException {
        final String is = "a";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aString, action);

        m.match(b);

        // Starting out accepting any kind of match
        verify(action).performMatch(b, 0, 0);
    }
}
