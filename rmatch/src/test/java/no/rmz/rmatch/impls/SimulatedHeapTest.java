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

import no.rmz.rmatch.utils.SimulatedHeap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test our simulated heap.
 */
@ExtendWith(MockitoExtension.class)
public final class SimulatedHeapTest {

    /**
     * The test article.
     */
    private SimulatedHeap<Integer> h;

    /**
     * An array of integers, with the property that i[j] = j and
     * 0 <= j <= maxi.
     */
    private Integer[] i;

    /**
     * The maximum integer stored in i.
     */
    private int maxi;


    /**
     * A comparator of integers.
     */
    private static final Comparator<Integer> CMP =
            new Comparator<Integer>() {
                @Override
                public int compare(final Integer t, final Integer t1) {
                    return t.compareTo(t1);
                }
            };

    /**
     * Number of items in the testheap.
     */
    private static final int NO_OF_ITEMS_IN_TESTHEAP = 100;

    /**
     * Set u the array "i" to contain integers i[j] = j for j in the range 0 ...
     * 99.
     */
    @BeforeEach
    public void setUp() {
        h = new SimulatedHeap<Integer>(CMP);

        maxi = NO_OF_ITEMS_IN_TESTHEAP;

        i = new Integer[maxi];
        for (int j = 0; j < maxi; j++) {
            i[j] = Integer.valueOf(j);
        }
    }

    /**
     * Test the corner case of adding then removing a single item from the heap.
     */
    @Test
    public void testAddOne() {
        assertTrue(h.isEmpty());
        h.add(i[1]);
        assertTrue(!h.isEmpty());
        assertTrue(h.getFirst() == i[1]);
        h.remove(i[1]);
        assertTrue(h.isEmpty());
    }

    /**
     * Add two elements in reversed order (forst second then first) and see that
     * the elements that are removed elements are in fact first the first and
     * then the second.
     */
    @Test
    public void test2() {
        assertTrue(h.isEmpty());
        h.add(i[2]);
        h.add(i[1]);
        assertTrue(h.getFirst() == i[1]);
        h.remove(i[1]);
        assertTrue(h.getFirst() == i[2]);
        h.remove(i[2]);
        assertTrue(h.isEmpty());
    }


    /**
     * A nice prime.
     */
    private static final int SEVENTEEN_IS_A_NICE_PRIME = 17;

    /**
     * Add elements in a pseudo-random order (addition of 73 modulo maxi). then
     * get/remove all elements, and they had better come out in the right order
     * ;)
     */
    @Test
    public void testInsertingALot() {
        int j = 0;
        int count = 0;

        assertTrue(h.isEmpty());
        do {
            j = (j + SEVENTEEN_IS_A_NICE_PRIME) % maxi;
            h.add(j);
            count += 1;
        } while (j != 0);
        assertEquals(maxi, count);

        for (j = 0; j < maxi; j++) {
            Integer first = h.getFirst();
            assertEquals(Integer.valueOf(j), first);
            h.remove(first);
        }
    }
}
