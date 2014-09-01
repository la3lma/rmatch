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

import static java.lang.Character.valueOf;
import static no.rmz.rmatch.testutils.GraphDumper.dump;

import no.rmz.rmatch.compiler.RegexpParserException;
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.impls.RegexpImpl;
import no.rmz.rmatch.interfaces.Action;
import no.rmz.rmatch.interfaces.Matcher;
import no.rmz.rmatch.interfaces.NDFACompiler;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import no.rmz.rmatch.interfaces.RegexpFactory;
import no.rmz.rmatch.interfaces.RegexpStorage;
import no.rmz.rmatch.mockedcompiler.CharPlusNode;

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
 * (this is not AI's A* search algorithm. :-)
 */
@RunWith(MockitoJUnitRunner.class)
public final class APlusTest {

    /**
     * A mocked action, used to check that actions are run.
     */
    @Mock
    Action action;

    /**
     * A mocked compiler.
     */
    @Mock
    NDFACompiler compiler;

    /**
     * A string containing the string "a+".
     */
    private String aplusString;

    /**
     * A matcher instance (test item) used during the test.
     */
    private Matcher m;

    /**
     *
     */
    private Regexp regexp;

    /**
     * Set up the context with all the strings, generate an
     * CharPlusNode instance for "a" and a RegexpFactory that
     * will get the mocked compilation result when asked to compile
     * the string "a+".
     */
    @Before
    public void setUp() throws RegexpParserException {
        final String finalaPlus = "a+";
        aplusString = finalaPlus;
        regexp = new RegexpImpl(aplusString);
        final NDFANode aPlusNode =
                new CharPlusNode(valueOf('a'), regexp, true);

        when(compiler.compile((Regexp) any(),
                (RegexpStorage) any())).thenReturn(aPlusNode);

        final RegexpFactory regexpFactory = new RegexpFactory() {
            @Override
            public Regexp newRegexp(final String regexpString) {
                if (regexpString.equals(finalaPlus)) {
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
        assertTrue("the regexp should not initially have actions",
                !regexp.hasActions());
        m.add(aplusString, action);
        assertTrue("the regexp should have actions", regexp.hasActions());
    }

    /**
     * Check that the string "ab" matches.
     */
    @Test
    public void testMockedMatchLength1bTerminated() throws RegexpParserException {
        final String is = "ab";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aplusString, action);

        m.match(b);

        dump("testMockedMatchLength1bTerminated",
                m.getNodeStorage());
        // Starting out accepting any kind of match
        verify(action).performMatch(b, 0, 0);
    }

    /**
     * Check that at match is found in  the string "bab".
     */
    @Test
    public void testMockedMatchLength1bTerminatedbPrefixed() throws RegexpParserException {
        final String is = "bab";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aplusString, action);

        m.match(b);
        dump("testMockedMatchLength1bTerminatedbPrefixed",
                m.getNodeStorage());

        verify(action).performMatch(b, 1, 1);
    }

    /**
     * Check that the string "a" matches.
     */
    @Test
    public void testMockedMatchLength1() throws RegexpParserException {
        final String is = "a";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aplusString, action);
        m.match(b);

        verify(action).performMatch(b, 0, 0);
    }

    /**
     * Test that the string "aa" matches.
     */
    @Test
    public void testMockedMatchLength2() throws RegexpParserException {
        final String is = "aa";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aplusString, action);

        m.match(b);


        verify(action).performMatch(b, 0, 1);
    }

    /**
     * Test that the string "aaa" matches.
     */
    @Test
    public void testMockedMatchLength3() throws RegexpParserException {
        final String is = "aaa";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aplusString, action);

        m.match(b);

        verify(action).performMatch(b, 0, 2);
    }

    /**
     * Find multiple matches for the string "ababaab".
     */
    @Test
    public void testMockedTripleMatchLength7() throws RegexpParserException {
        final String is = "ababaab";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aplusString, action);

        m.match(b);
        dump("testMockedTripleMatchLength7",
                m.getNodeStorage());


        verify(action).performMatch(b, 0, 0);
        verify(action).performMatch(b, 2, 2);
        verify(action).performMatch(b, 4, 5);
    }
}
