/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package no.rmz.rmatch.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.*;
import java.util.concurrent.locks.*;

/**
 * Simulating a heap using a TreeMap.
 *
 * @param <T> The type of the elements in the simulated heap.
 */
public final class Heap<T> {

    private final static int INITIAL_CAPACITY = 10;

    /**
     * We use a TreeMap to represent the heap.
     */
    private final PriorityQueue<T> tm;

    /**
     * To optimize access across threads, we use a read/write lock.
     */
    private final ReadWriteLock rwl
            = new ReentrantReadWriteLock();

    private final Lock rl = rwl.readLock();

    private final Lock wl = rwl.writeLock();

    /**
     * Create a new simulated heap using the comparator c.
     *
     * @param c a comparator of T elements.
     */
    public Heap(final Comparator<T> c) {
        checkNotNull(c);
        tm = new PriorityQueue<T>(INITIAL_CAPACITY, c);
    }

    /**
     * Add an element to the heap.
     *
     * @param m the element to add.
     */
    public void add(final T m) {
        checkNotNull(m);
        wl.lock();
        try {
            tm.add(m);
        } finally {
            wl.unlock();
        }
    }

    /**
     * Remove an element from the heap. Will throw a runtime exception when
     * attempting to remove something that isn't stored in the heap.
     *
     * @param m the item to remove.
     */
    public void remove(final T m) {
        checkNotNull(m);
        wl.lock();
        try {
            checkArgument(!tm.isEmpty());
            boolean removed = tm.remove(m);
            if (!removed) {
                throw new RuntimeException(
                        "Attempt to remove nonexisting "
                        + "content from a SimulatedHeap");
            }
        } finally {
            wl.unlock();
        }
    }

    /**
     * Get the first element of the heap (the smallest element).
     *
     * @return the smallest element of the heap.
     */
    public T getFirst() {
        rl.lock();
        try {
            return tm.peek();
        } finally {
            rl.unlock();
        }
    }

    /**
     * True iff the heap contains no elements.
     *
     * @return true iff empty.
     */
    public boolean isEmpty() {
        rl.lock();
        try {
            return tm.isEmpty();
        } finally {
            rl.unlock();
        }
    }

    @Override
    public String toString() {
        rl.lock();
        try {
            return "Heap{" + "tm=" + tm + '}';
        } finally {
            rl.unlock();
        }
    }

    /**
     * True iff the heap contains element m.
     *
     * @param m the element to check for.
     * @return True iff the element is there.
     */
    public boolean contains(final T m) {
        rl.lock();
        try {
            return tm.contains(m);
        } finally {
            rl.unlock();
        }
    }

    /**
     * Returns the number of element in the heap.
     *
     * @return no of elements in the heap.
     */
    public int size() {
        rl.lock();
        try {
            return tm.size();
        } finally {
            rl.unlock();
        }
    }
}
