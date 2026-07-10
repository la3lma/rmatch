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
package no.rmz.rmatch.impls;

import static no.rmz.rmatch.internal.Checks.checkNotNull;
import static no.rmz.rmatch.internal.Checks.checkState;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import no.rmz.rmatch.interfaces.Match;

/**
 * Priority queue used to decide which overlapping match candidates may run.
 *
 * <p>The matcher can have several legal candidates for the same expression and input region. The
 * domination heap keeps those candidates ordered so dominated candidates can be suppressed before
 * actions are invoked.
 */
final class DominationHeap {

  private final PriorityBlockingQueue<Match> heap;

  /** Create a domination heap using the default match-domination ordering. */
  DominationHeap() {
    this(Match.COMPARE_BY_DOMINATION);
  }

  /**
   * Create a domination heap with an explicit ordering.
   *
   * @param comparator ordering used to decide candidate priority
   */
  DominationHeap(final Comparator<Match> comparator) {
    heap = new PriorityBlockingQueue<>(11, comparator);
  }

  /**
   * Add a match candidate to the heap.
   *
   * @param m candidate to add
   */
  public void addMatch(final Match m) {
    checkNotNull(m);
    heap.add(m);
  }

  /**
   * Remove a match candidate from the heap.
   *
   * @param m candidate to remove
   */
  public void remove(final Match m) {
    checkNotNull(m);
    checkState(!isEmpty());
    //noinspection ResultOfMethodCallIgnored
    heap.remove(m);
  }

  /**
   * Return whether the heap contains no candidates.
   *
   * @return {@code true} if empty
   */
  public boolean isEmpty() {
    return heap.isEmpty();
  }

  /**
   * Return the highest-priority candidate without removing it.
   *
   * @return first candidate, or {@code null} if the heap is empty
   */
  public Match getFirstMatch() {
    return heap.peek();
  }

  /**
   * Return whether the heap contains a candidate.
   *
   * @param m candidate to look up
   * @return {@code true} if {@code m} is present
   */
  public boolean containsMatch(final Match m) {
    return heap.contains(m);
  }

  /**
   * Return the number of candidates in the heap.
   *
   * @return candidate count
   */
  public int size() {
    return heap.size();
  }
}
