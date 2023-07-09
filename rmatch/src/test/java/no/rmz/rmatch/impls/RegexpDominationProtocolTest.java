/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *      http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.impls;

import no.rmz.rmatch.interfaces.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing the domination protocol, with contributions
 * from misc. classes.
 */
public final class RegexpDominationProtocolTest {

    /**
     * The test article.
     */
    private RegexpImpl re;


    /**
     * A node serving as our startnode.
     */
    private DFANodeImpl sn; // StartNode


    /**
     * Give us a regexp impleentation and a dfanode
     * representing no NDFAnodes (the empty set).
     */
    @BeforeEach
    public void setUp() {
        re = new RegexpImpl("Fnord");
        sn = new DFANodeImpl(Collections.EMPTY_SET);
    }

    /**
     * Create a match set, generate a new match
     * associated with this matchset, and observe that
     * the regular expression has matches associated with it.
     */
    @Test
    public void testRegisterMatch() {
        final MatchSetImpl ms = new MatchSetImpl(1, sn, null);
        // The variable m isn't used, but since we makea new
        // match impl, it will indirectly affect the result of the test

        new MatchImpl(ms, re); // Implicit registration!
        assertTrue(re.hasMatches());  // Due to implicit registration
    }

    /**
     * Establish a match associated with a regular expression, then
     * abandon that match, and then observe that the regexp is no longer
     * associated with that match.
     */
    @Test
    public void testAbandonMatch() {
        final MatchSetImpl ms = new MatchSetImpl(1, sn, null);
        final MatchImpl m = new MatchImpl(ms, re);
        assertTrue(re.hasMatches());
        re.abandonMatch(m, null);
        assertFalse(re.hasMatches());
    }

    /**
     * Test that a match starting at point 1 and ending at point 2
     * will be dominated by a match starting at point 1 and ending
     * at point 3.
     */
    @Test
    public void testSingleMatchDomination() {

        final int startPoint = 1;
        final int endPoint1 = 2;
        final int endPoint2 = 3;

        final MatchSetImpl ms = new MatchSetImpl(startPoint, sn, null);
        final MatchImpl m1 = new MatchImpl(ms, re);
        m1.setEnd(endPoint1);
        final MatchImpl m2 = new MatchImpl(ms, re);
        m2.setEnd(endPoint2);

        assertTrue(Match.COMPARE_BY_DOMINATION.compare(m2, m1) < 0);
        assertFalse(Match.COMPARE_BY_DOMINATION.compare(m1, m2) < 0);
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m1, m1));
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m2, m2));
    }

    /**
     * Test that a match starting at point 1 and ending at point 3
     * will dominate a match starting at point 2 and ending at point 3.
     */
    @Test
    public void testOverlappingDominatedMatchAddition() {

        final int startPoint1 = 1;
        final int startPoint2 = 2;
        final int endpoint = 3;

        final MatchSetImpl ms1 = new MatchSetImpl(startPoint1, sn, null);
        final MatchSetImpl ms2 = new MatchSetImpl(startPoint2, sn, null);
        final MatchImpl m1 = new MatchImpl(ms2, re);
        m1.setEnd(endpoint);
        final MatchImpl m2 = new MatchImpl(ms1, re);
        m2.setEnd(endpoint);


        assertTrue(Match.COMPARE_BY_DOMINATION.compare(m2, m1) < 0);
        assertFalse(Match.COMPARE_BY_DOMINATION.compare(m1, m2) < 0);
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m1, m1));
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m2, m2));


        assertTrue(Match.COMPARE_BY_DOMINATION.compare(m2, m1) < 0);
        assertFalse(Match.COMPARE_BY_DOMINATION.compare(m1, m2) < 0);
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m1, m1));
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m2, m2));
    }

    /**
     * XXX I Need to grok this test.
     */
    @Test
    public void doNotestMultipleOverlappingDominatedMatchAddition() {

        final int start = 0;
        final int end = 5;

        checkArgument(true);

        // The zeroeth element is null (padding)
        final int noOfElements = end - start + 1;

        checkArgument(true);

        final MatchSetImpl[] ms = new MatchSetImpl[noOfElements]; // Null pad
        final Match[] m = new MatchImpl[noOfElements]; //Null padd

        for (int i = start; i <= end; i++) {
            checkNotNull(sn);
            ms[i] = new MatchSetImpl(i, sn, null);
            checkNotNull(ms[i], "Expected ms[" + i + "] to be non-null");
            m[i] = new MatchImpl(ms[i], re);
            m[i].setEnd(end);
        }


        for (int i = start + 1; i < end; i++) {
            for (int j = i + 1; j < end; j++) {
                final Match a1 = m[i - 1];
                final Match a2 = m[j - 1];
                assertNotNull(a1, "a1 must not be null");
                assertNotNull(a2, "a2 must not be null");
                assertTrue(Match.COMPARE_BY_DOMINATION.compare(a1, a2) < 0);
                assertFalse(Match.COMPARE_BY_DOMINATION.compare(a2, a1) < 0);
            }
        }

        for (int i = start + 1; i < end; i++) {
            final Match a = m[i - 1];
            checkNotNull(a);
            assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(a, a));
        }
    }

    /**
     * XXX I Need to grok this test.
     */
    @Test
    public void testOverlappingNondominatingMatchAddition() {

        final int startMs1 = 1;
        final int startMs2 = 2;
        final int endMs1 = 3;
        final int endMs2 = 4;
        final MatchSetImpl ms1 = new MatchSetImpl(startMs1, sn, null);
        final MatchSetImpl ms2 = new MatchSetImpl(startMs2, sn, null);
        final MatchImpl m1 = new MatchImpl(ms1, re);
        m1.setEnd(endMs1);
        final MatchImpl m2 = new MatchImpl(ms2, re);
        m2.setEnd(endMs2);

        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m2, m1));
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m1, m2));
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m1, m1));
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m2, m2));
    }

    /**
     * XXX I Need to grok this test.
     */
    @Test
    public void testOverlappingDifferentRegexpsMatchAddition() {

        final int startPoint = 1;
        final int endpoint2 = 3;
        final int endpoint1 = 2;

        final RegexpImpl re2 = new RegexpImpl("Foobar");
        final MatchSetImpl ms = new MatchSetImpl(startPoint, sn, null);
        final MatchImpl m1 = new MatchImpl(ms, re);
        m1.setEnd(endpoint1);
        final MatchImpl m2 = new MatchImpl(ms, re2);
        m2.setEnd(endpoint2);

        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m2, m1));
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m2, m2));
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m1, m1));
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(m1, m2));
    }
    // XXX @@@ Domination combined with abandonment isn't tested
    // XXX     No part of strong domination is tested.
}
