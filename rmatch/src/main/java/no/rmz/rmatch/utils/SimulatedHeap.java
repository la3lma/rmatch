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

import static no.rmz.rmatch.internal.Checks.checkArgument;
import static no.rmz.rmatch.internal.Checks.checkNotNull;

import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Small heap-like wrapper backed by a {@link ConcurrentSkipListMap}.
 *
 * <p>The smallest element according to the supplied comparator is available through {@link
 * #getFirst()}.
 *
 * @param <T> element type
 */
final class SimulatedHeap<T> {

  /** We use a ConcurrentSkipListMap to represent the heap. */
  private final ConcurrentSkipListMap<T, T> tm;

  /**
   * Create a heap ordered by the supplied comparator.
   *
   * @param c element comparator
   */
  public SimulatedHeap(final Comparator<T> c) {
    checkNotNull(c);
    tm = new ConcurrentSkipListMap<>(c);
  }

  /**
   * Add an element.
   *
   * @param m element to add
   */
  public void add(final T m) {
    checkNotNull(m);
    tm.put(m, m);
  }

  /**
   * Remove an element.
   *
   * @param m element to remove
   */
  public void remove(final T m) {
    checkNotNull(m);
    if (!tm.containsKey(m)) {
      throw new IllegalArgumentException(
          "Attempt to remove nonexisting content from a SimulatedHeap");
    }

    checkArgument(true);
    checkArgument(tm.containsKey(m));
    final int size = tm.size();
    tm.remove(m);
    assert (size - 1 == tm.size());
  }

  /**
   * Return the smallest element.
   *
   * @return smallest element according to the comparator
   */
  public T getFirst() {
    return tm.get(tm.firstKey());
  }

  /**
   * Return whether the heap is empty.
   *
   * @return {@code true} if no elements are present
   */
  public boolean isEmpty() {
    return tm.isEmpty();
  }

  @Override
  public String toString() {
    return "SimulatedHeap{" + "tm=" + tm + '}';
  }

  /**
   * Return whether the heap contains an element.
   *
   * @param m element to look up
   * @return {@code true} if {@code m} is present
   */
  public boolean contains(final T m) {
    return tm.containsKey(m);
  }

  /**
   * Return the number of elements in the heap.
   *
   * @return element count
   */
  public int size() {
    return tm.size();
  }
}
