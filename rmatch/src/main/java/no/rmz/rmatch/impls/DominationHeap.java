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

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class DominationHeap {

    private static final Logger LOG = Logger.getLogger(DominationHeap.class.getName());

    private final PriorityBlockingQueue<Match> heap;

    public DominationHeap() {
        this(Match.COMPARE_BY_DOMINATION);
    }

    public DominationHeap(final Comparator<Match> comparator) {
        heap = new PriorityBlockingQueue<>(11, comparator);
    }

    public void addMatch(final Match m) {
        checkNotNull(m);
        heap.add(m);
    }

    public void remove(final Match m) {
        checkNotNull(m);
        checkState(!isEmpty());
        heap.remove(m);
    }

    public boolean isEmpty() {
        return heap.isEmpty();
    }

    public Match getFirstMatch() {
        return heap.peek();
    }

    public boolean containsMatch(final Match m) {
        return heap.contains(m);
    }

    public int size() {
        return heap.size();
    }
}
