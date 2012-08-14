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

package no.rmz.rmatch.testutils;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import no.rmz.rmatch.utils.SortedSetComparatorImpl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 *   Check the SortedSetComparatorImpl for integers.
 */
public final class ComparableSetTest {


    /**
     * The first sorted set of integers.
     */
    private SortedSet<Integer> instance1;

    /**
     * The second sorted set of integers.
     */
    private SortedSet<Integer> instance2;

    /**
     * A comparator instance.
     */
    private Comparator<SortedSet<Integer>> cmp;

    /**
     * Set up the test articles.
     */
    @Before
    public  void setUp() {
        instance1 = new TreeSet<Integer>();
        instance2 = new TreeSet<Integer>();
        cmp = new SortedSetComparatorImpl<Integer>();
    }

    /**
     * Compare two empty sets (but distinct instances).
     */
    @Test
    public  void testEqualIdenticalEmptySets() {
        assertEquals(0, cmp.compare(instance1, instance2));
    }


    /**
     * Test two sets containing the same non-empty content.
     */
    @Test
    public  void testEqualIdenticalSingeltonSets() {
        instance1.add(Integer.valueOf(1));
        instance2.add(Integer.valueOf(1));
        assertEquals(0, cmp.compare(instance1, instance2));
    }

    /**
     * Test two sets with different content.
     */
    @Test
    public  void testNonIdenticalSingeltonSets() {
        instance1.add(Integer.valueOf(1));
        instance2.add(Integer.valueOf(2));
        assertTrue(cmp.compare(instance1, instance2) < 0);
    }
}
