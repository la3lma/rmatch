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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import java.util.Comparator;
import java.util.logging.Logger;
import no.rmz.rmatch.interfaces.Match;
import no.rmz.rmatch.utils.SimulatedHeap;

/**
 *
 * A heap implementation where the comparision operator is compararison by
 * domination. This means that the smallest element is the completely
 * undominated element.
 * <p>
 * If a match starting earlier and ending at the same place or later exists for
 * the same regexp, the later-starting match is dominated.
 * <p>
 * Any additional rules are only introduced to improve efficiency, by abandoning
 * later matches when states are found that indicates that they can never be
 * un-dominated.
 *
 */
public final class DominationHeap {

    /**
     * Our dear Log.
     */
    private static final Logger LOG =
            Logger.getLogger(DominationHeap.class.getName());
    /**
     * A simulated heap of matches.
     */
    private final SimulatedHeap<Match> heap;

    /**
     * Create a new instance.
     */
    public DominationHeap() {
        this(Match.COMPARE_BY_DOMINATION);
    }

    /**
     * Inject any comparator you want. The default comparator is
     * Match.COMPARE_BY_DOMINATION, but sometimes, and in particular when
     * testing.
     *
     * @param comparator the comparator used to find the smallest element of the
     * heap.
     */
    public DominationHeap(final Comparator<Match> comparator) {
        heap = new SimulatedHeap<>(comparator);
    }

    /**
     * Add a match to the heap.
     *
     * @param m the match to add.
     */
    public void addMatch(final Match m) {
        checkNotNull(m);
        synchronized (heap) {
            heap.add(m);
        }
    }

    /**
     * Remove a match from the heap. Throw an unchecked exception if the heap
     * contains no matches.
     *
     * @param m the Match to remove.
     */
    public void remove(final Match m) {
        checkNotNull(m);
        synchronized (heap) {
            checkState(!isEmpty());
            heap.remove(m);
        }
    }

    /**
     * Check if the heap if empty.
     *
     * @return true iff empty.
     */
    public boolean isEmpty() {
        synchronized (heap) {
            return heap.isEmpty();
        }
    }

    /**
     * Get the first (smallest) match of the heap.
     *
     * @return the first heap in the heap, or null if empty
     */
    public Match getFirstMatch() {
        synchronized (heap) {
            return heap.getFirst();
        }
    }

    /**
     * Check if the heap contains a match.
     *
     * @param m the match to check for
     * @return true iff m is in the heap.
     */
    public boolean containsMatch(final Match m) {
        synchronized (heap) {
            return heap.contains(m);
        }
    }

    /**
     * The number of matches in the heap.
     *
     * @return the size of the heap.
     */
    public int size() {
        synchronized (heap) {
            return heap.size();
        }
    }
}
