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

import no.rmz.rmatch.impls.DominationHeap;
import java.util.Comparator;
import no.rmz.rmatch.interfaces.Match;
import no.rmz.rmatch.interfaces.Regexp;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import org.mockito.Mock;
import static org.mockito.Mockito.*;

import org.mockito.runners.MockitoJUnitRunner;

/**
 * Test that the domination protocol does indeed work as advertised.
 */
@RunWith(MockitoJUnitRunner.class)
public class DominationHeapTest {

    /**
     * A domination heap.  Really a part of the fixture since we're
     * just testing the comparator.
     */
    DominationHeap heap;

    /**
     * A mocked match.
     */
    @Mock
    Match m1;

    /**
     * A mocked match.
     */
    @Mock
    Match m2;

    /**
     * A mocked regexp.
     */
    @Mock
    Regexp r;

    /**
     * A mocked comparator for matches.
     * XXX Swhat does it do?
     */
    @Mock
    Comparator<Match> comparator;

    /**
     * Set up test items and surrounding context.  IN particular, set up
     * the m1>m2 ordering that will be used in many a situation of this
     * test.  The ordering is implemented using mocking.
     */
    @Before
    public void setUp() {
        heap = new DominationHeap(comparator);

        // Setting up ordering between m1 and m2
        // m1>m2
        when(comparator.compare(m1, m1)).thenReturn(0);
        when(comparator.compare(m2, m2)).thenReturn(0);

        when(comparator.compare(m1, m2)).thenReturn(1);
        when(comparator.compare(m2, m1)).thenReturn(-1);

        when(m1.getRegexp()).thenReturn(r);
        when(m2.getRegexp()).thenReturn(r);
    }

    /**
     * Adding an element to the heap.
     */
    @Test
    public void testAddMatchOne() {
        assertEquals(0, heap.size());
        heap.addMatch(m1);
        assertEquals(1, heap.size());
    }

    /**
     * Adding an element to an empty heap, then removing it and
     * again getting an empty heap.
     */
    @Test
    public void testRemoveOne() {
        assertTrue(heap.isEmpty());
        heap.addMatch(m1);
        assertTrue(!heap.isEmpty());
        heap.remove(m1);
        assertTrue(heap.isEmpty());
    }

    /**
     * Test adding a single element to the heap.
     */
    @Test
    public void testIsEmptyOne() {
        assertTrue(heap.isEmpty());
        heap.addMatch(m1);
        assertTrue(!heap.isEmpty());
    }

    /**
     * Test adding two elements to the heap.
     */
    @Test
    public void testAddMatchTwo() {
        assertEquals(0, heap.size());
        heap.addMatch(m1);
        assertEquals(1, heap.size());
        heap.addMatch(m2);
        assertEquals(2, heap.size());
    }

    /**
     * Test adding, then removing two elements to the heap.
     */
    @Test
    public void testRemoveTwo() {
        assertTrue(heap.isEmpty());
        heap.addMatch(m1);
        heap.addMatch(m2);
        assertEquals(2, heap.size());
        heap.remove(m2);
        assertEquals(1, heap.size());
        heap.remove(m1);
        assertTrue(heap.isEmpty());
    }
}
