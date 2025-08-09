/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.rmz.rmatch.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Simulating a heap using a PriorityBlockingQueue.
 *
 * @param <T> The type of the elements in the simulated heap.
 */
public final class SimulatedHeapConcurrent<T> {

  private static final int INITIAL_CAPACITY = 2;

  /** We use a TreeMap to represent the heap. */
  private final PriorityBlockingQueue<T> pbq;

  /**
   * Create a new simulated heap using the comparator c.
   *
   * @param c a comparator of T elements.
   */
  public SimulatedHeapConcurrent(final Comparator<T> c) {
    checkNotNull(c);
    pbq = new PriorityBlockingQueue<>(INITIAL_CAPACITY, c);
  }

  /**
   * Add an element to the heap.
   *
   * @param m the element to add.
   */
  public void add(final T m) {
    checkNotNull(m);
    pbq.put(m);
  }

  /**
   * Remove an element from the heap. Will throw a runtime exception when attempting to remove
   * something that isn't stored in the heap.
   *
   * @param m the item to remove.
   */
  public void remove(final T m) {
    checkNotNull(m);

    if (pbq.isEmpty()) {
      throw new RuntimeException("Attempt to remove content from empty queue");
    }
    if (!pbq.remove(m)) {
      throw new RuntimeException("Attempt to remove nonexisting " + "content from a SimulatedHeap");
    }
  }

  /**
   * Get the first element of the heap (the smallest element).
   *
   * @return the smallest element of the heap.
   */
  public T getFirst() {
    return pbq.peek();
  }

  /**
   * True iff the heap contains no elements.
   *
   * @return true iff empty.
   */
  public boolean isEmpty() {
    return pbq.isEmpty();
  }

  @Override
  public String toString() {
    return "SimulatedHeap{" + "tm=" + pbq + '}';
  }

  /**
   * True iff the heap contains element m.
   *
   * @param m the element to check for.
   * @return True iff the element is there.
   */
  public boolean contains(final T m) {
    return pbq.contains(m);
  }

  /**
   * Returns the number of element in the heap.
   *
   * @return no of elements in the heap.
   */
  public int size() {
    return pbq.size();
  }
}
