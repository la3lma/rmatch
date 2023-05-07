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

package no.rmz.rmatch.impls;

import no.rmz.rmatch.interfaces.Match;
import no.rmz.rmatch.interfaces.MatchSet;
import no.rmz.rmatch.interfaces.NDFANode;
import no.rmz.rmatch.interfaces.Regexp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Representation of an actual or potential match.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MatchImplTest {

   /**
    * A mocked match set, used to put the match in a
    * familiar context.
    */
    @Mock
    MatchSet ms;

    /**
     * a test article regexp.
     */
    private Regexp regexpReal;

    /**
     * A mocked regexp.
     */
    @Mock
    Regexp regexpMocked;

    /**
     * A holder of runnable matches, mocked out.
     */
    @Mock
    RunnableMatchesHolder mockRunnableMatches;

    /**
     * A test article.
     */
    private Match match;

    /**
     * A real implementation, but the components it contains
     * are mocked.
     */
    private Match semiMockedMatch;

    /**
     * Set up the test articles.
     */
    @BeforeEach
    public void setUp() {
        regexpReal = new RegexpImpl("jadda");
        match = new MatchImpl(ms, regexpReal);
        semiMockedMatch = new MatchImpl(ms, regexpMocked);
    }

    /**
     * Test of isActiveFor method, of class Match.
     */
    @Test
    public final void testIsActiveFor() {
        // Check active (default)
        assertEquals(true, match.isActive());
        assertTrue(regexpReal.hasMatches());
        assertTrue(regexpReal.hasMatch(match));

        // Check active (after abandoning)
        match.abandon();
        assertEquals(false, match.isActive());
    }

   /**
    * Test creation of a match instance.
    */
    @Test
    public void testCreation() {
        final MatchSet msx =
                new MatchSetImpl(1,
                new DFANodeImpl((Set<NDFANode>) Collections.EMPTY_SET));
        final Regexp r = new RegexpImpl("Krasnji Octobr");
        assertTrue(!r.hasMatches());
        final Match m = new MatchImpl(msx, r);
        assertTrue(r.hasMatches());
        assertEquals(r, m.getRegexp());
        assertEquals(m.getStart(), msx.getStart());
        assertEquals(1, m.getStart());
    }

    /**
     * Check that when comparing a match by domination to null,
     * a null pointer exception is thrown.
     */
    @Test
    public final void testCompareToNull() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            Match.COMPARE_BY_DOMINATION.compare(match, null);
        });
    }

    /**
     * Check that two presumably equal matches are indeed equal when
     * compared using domination.
     */
    @Test
    public final void testCompareOtherWithNonmatchingRegex() {
        final Match match2 = new MatchImpl(ms, regexpMocked);
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(match, match2));
    }

    /**
     * Whe domination comparing, a match is equal to itself.
     */
    @Test
    public final void testCompareOtherSelf() {
        assertEquals(0, Match.COMPARE_BY_DOMINATION.compare(match, match));
    }

   /**
    * A final and inactive match will when it is committed be sent
    * to a set of runnable matches.
    *
    * XXX  The negation isn't tested!
    */
    @Test
    public final void testCommitDominatingOnFinalMatch() {
        match.setIsFinal();
        match.setInactive();
        MatchSetImpl.commitMatch(match, mockRunnableMatches);
        verify(mockRunnableMatches).add(match);
    }

    /**
     * XXX Don't really understand this test.
     */
    @Test
    public final void testCommitNotDominatingButStronglyDominatingOnFinalMatch()
    {
        match.setIsFinal();
        match.setInactive();
        when(regexpMocked.isDominating(semiMockedMatch)).thenReturn(false);
        when(regexpMocked.isStronglyDominated(semiMockedMatch))
                .thenReturn(true);
        MatchSetImpl.commitMatch(match, mockRunnableMatches);
        verify(mockRunnableMatches, never()).add(semiMockedMatch);

    }

    /**
     *  XXX Don't really understand this test.
     */
    @Test
    public final void testCommitNonDominatingOnFinalMatch() {
        semiMockedMatch.setIsFinal();
        semiMockedMatch.setInactive();
        MatchSetImpl.commitMatch(semiMockedMatch, mockRunnableMatches);
        verify(mockRunnableMatches, never()).add(match);
        //  verify(rexp).abandon(match);
    }

    /**
     * Test of getMatchSet method, of class MatchImpl.
     */
    @Test
    public final void testGetMatchSet() {
        final MatchSet result = match.getMatchSet();
        assertEquals(ms, result);
    }

    /**
     * Test of setFinal method, of class Match.
     */
    @Test
    public final void testSetFinal() {
        assertFalse(match.isFinal());
        match.setIsFinal();
        assertTrue(match.isFinal());
    }

    /**
     * Test of getStart method, of class Match.
     */
    @Test
    public final void testGetStart() {
        int result = match.getStart();
        assertEquals(0, result);
    }

    /**
     * Just this number we need.
     */
    private static final int ARBITRARY_END_JUST_FOR_TESTING = 100;

    /**
     * Test of setEnd method, of class Match.
     */
    @Test
    public final void testSetGetEnd() {
        assertTrue(match.getEnd() != 100);
        match.setEnd(ARBITRARY_END_JUST_FOR_TESTING);
        assertEquals(100, match.getEnd());
    }

    /**
     * Test of getRegexp method, of class Match.
     */
    @Test
    public final void testGetRegexp() {
        final Regexp result = match.getRegexp();
        assertEquals(regexpReal, result);
    }
    
    
}
