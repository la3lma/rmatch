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
public final class AorBtest {

    /**
     * Mocked, used to check for actions.
     */
    @Mock
    Action action;

    /**
     * Compiler.  Mocke to deliver precooked results.
     */
    @Mock
    NDFACompiler compiler;

    /**
     * A string: "a|b".
     */
    String aOrBString;


    /**
     * The test article, the macher.
     */
    private Matcher m;

    /**
     * RThe test article: The regexp.
     */
    Regexp regexp;

    /**
     * Set up test article and mocks.
     */
    @Before
    public void setUp() throws RegexpParserException {
        final String finalAorB = "a|b";
        aOrBString = finalAorB;
        regexp = new RegexpImpl(aOrBString);
        final NDFANode aPlusNode =
                new AlternativeCharsNode(
                valueOf('a'),
                valueOf('b'),
                regexp);

        when(compiler.compile((Regexp) any(),
                (RegexpStorage) any())).thenReturn(aPlusNode);

        final RegexpFactory regexpFactory = new RegexpFactory() {
            @Override
            public Regexp newRegexp(final String regexpString) {
                if (regexpString.equals(finalAorB)) {
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
        m.add(aOrBString, action);
        assertTrue("the regexp should have actions", regexp.hasActions());
    }

    /**
     * Look for match in first alternative.
     */
    @Test
    public void testFirstAlternative() throws RegexpParserException {
        final String is = "ab";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aOrBString, action);

        m.match(b);

        // Starting out accepting any kind of match
        verify(action).performMatch(b, 0, 0);
    }


    /**
     * Look for match in second alternative.
     */
    @Test
    public void testSecondAlternative() throws RegexpParserException {
        final String is = "cb";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aOrBString, action);

        m.match(b);

        // Starting out accepting any kind of match
        verify(action).performMatch(b, 1, 1);
    }

    /**
     * Look for match of first alternative sandwiched between two
     * nonmatching characters.
     */
    @Test
    public void testMatchForFirstInSandwichedPositon() throws RegexpParserException {
        final String is = "cac";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aOrBString, action);

        m.match(b);

        // Starting out accepting any kind of match
        verify(action).performMatch(b, 1, 1);
    }

    /**
     * Look for match of second alternative sandwiched between to nonmatching
     * characters.
     */
    @Test
    public void testMatchForSecondInSandwichedPositon() throws RegexpParserException {
        final String is = "cbc";
        final no.rmz.rmatch.utils.StringBuffer b =
                new no.rmz.rmatch.utils.StringBuffer(is);
        m.add(aOrBString, action);

        m.match(b);

        // Starting out accepting any kind of match
        verify(action).performMatch(b, 1, 1);
    }
}
