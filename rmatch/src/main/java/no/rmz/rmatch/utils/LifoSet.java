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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Set with last-in, first-out iteration semantics.
 *
 * <p>Adding an element that is already present has no effect. Popping returns the most recently
 * added element that has not yet been popped.
 *
 * @param <T> element type
 */
public final class LifoSet<T> {

  /** Set used for membership tests. */
  private final Set<T> members = new HashSet<>();

  /** A deque that is used to represent the LIFO aspect of the LIFO set. */
  private final Deque<T> lifo = new ArrayDeque<>();

  /**
   * Return whether the set is empty.
   *
   * @return {@code true} when no elements are present
   */
  public boolean isEmpty() {
    synchronized (members) {
      return members.isEmpty();
    }
  }

  /**
   * Add an element if it is not already present.
   *
   * @param t the element to be added
   * @return {@code true} if the element was newly added
   */
  public boolean add(final T t) {
    synchronized (members) {
      if (!members.contains(t)) {
        members.add(t);
        lifo.addLast(t);
        return true;
      } else {
        return false;
      }
    }
  }

  /**
   * Remove and return the most recently added element.
   *
   * @return most recently added element still present in the set
   */
  public T pop() {
    synchronized (members) {
      if (isEmpty()) {
        throw new IllegalStateException("Attempt to remove something from an empty LifoSet");
      }
      final T result = lifo.removeLast();
      members.remove(result);
      return result;
    }
  }

  /**
   * Add all elements from a set.
   *
   * @param elementSet elements to add, in the order supplied by the set iterator
   */
  public void addAll(final Set<T> elementSet) {
    synchronized (members) {
      for (final T t : elementSet) {
        add(t);
      }
    }
  }

  /**
   * Return whether an element is present.
   *
   * @param element element to look up
   * @return {@code true} if {@code element} is present
   */
  public boolean contains(final T element) {
    synchronized (members) {
      return members.contains(element);
    }
  }

  @Override
  public String toString() {
    synchronized (members) {
      return "LifoSet{" + "lifo=" + lifo + '}';
    }
  }
}
