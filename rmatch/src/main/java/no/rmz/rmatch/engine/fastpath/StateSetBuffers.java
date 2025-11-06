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
package no.rmz.rmatch.engine.fastpath;

import java.util.BitSet;

/**
 * Thread-local pool of reusable scratch buffers for state-set operations.
 *
 * <p>This class provides per-thread scratch buffers to avoid allocations during NFA/DFA state
 * operations. Each thread gets its own set of buffers, eliminating the need for synchronization
 * while avoiding repeated allocations in hot matching paths.
 *
 * <p>Key optimizations:
 *
 * <ul>
 *   <li>Thread-local storage avoids synchronization overhead
 *   <li>Reusable BitSet and int[] arrays reduce GC pressure
 *   <li>Clear() operations are cheaper than creating new objects
 *   <li>Buffers are sized appropriately for typical workloads
 * </ul>
 */
public final class StateSetBuffers {

  /** Default size for state set arrays. */
  private static final int DEFAULT_STATE_SET_SIZE = 256;

  /** Thread-local instance of buffers. */
  private static final ThreadLocal<StateSetBuffers> INSTANCE =
      ThreadLocal.withInitial(StateSetBuffers::new);

  /** BitSet for epsilon-closure operations. */
  private final BitSet epsilonClosureBitSet;

  /** BitSet for next-state-set operations. */
  private final BitSet nextStateBitSet;

  /** Integer array for state IDs. */
  private final int[] stateIdArray;

  /** Temporary BitSet for intermediate operations. */
  private final BitSet tempBitSet;

  /** Private constructor - use get() to obtain instance. */
  private StateSetBuffers() {
    this.epsilonClosureBitSet = new BitSet(DEFAULT_STATE_SET_SIZE);
    this.nextStateBitSet = new BitSet(DEFAULT_STATE_SET_SIZE);
    this.tempBitSet = new BitSet(DEFAULT_STATE_SET_SIZE);
    this.stateIdArray = new int[DEFAULT_STATE_SET_SIZE];
  }

  /**
   * Get the thread-local StateSetBuffers instance.
   *
   * @return the buffers for the current thread
   */
  public static StateSetBuffers get() {
    return INSTANCE.get();
  }

  /**
   * Get a reusable BitSet for epsilon-closure operations.
   *
   * <p>The BitSet is cleared before being returned. The caller should use it immediately and not
   * store references to it.
   *
   * @return a cleared BitSet for epsilon-closure
   */
  public BitSet getEpsilonClosureBitSet() {
    epsilonClosureBitSet.clear();
    return epsilonClosureBitSet;
  }

  /**
   * Get a reusable BitSet for next-state operations.
   *
   * <p>The BitSet is cleared before being returned. The caller should use it immediately and not
   * store references to it.
   *
   * @return a cleared BitSet for next-state
   */
  public BitSet getNextStateBitSet() {
    nextStateBitSet.clear();
    return nextStateBitSet;
  }

  /**
   * Get a reusable BitSet for temporary operations.
   *
   * <p>The BitSet is cleared before being returned. The caller should use it immediately and not
   * store references to it.
   *
   * @return a cleared BitSet for temporary use
   */
  public BitSet getTempBitSet() {
    tempBitSet.clear();
    return tempBitSet;
  }

  /**
   * Get a reusable int array for state IDs.
   *
   * <p>The array is NOT cleared before being returned. The caller is responsible for only using the
   * portion of the array they fill and not relying on zero-initialization.
   *
   * @return an int array for state IDs
   */
  public int[] getStateIdArray() {
    return stateIdArray;
  }

  /**
   * Get the maximum size supported by these buffers.
   *
   * @return the maximum number of states these buffers can handle
   */
  public int getMaxSize() {
    return DEFAULT_STATE_SET_SIZE;
  }

  /**
   * Clear all buffers explicitly.
   *
   * <p>This is typically not needed as buffers are cleared when retrieved, but can be used for
   * explicit cleanup if desired.
   */
  public void clear() {
    epsilonClosureBitSet.clear();
    nextStateBitSet.clear();
    tempBitSet.clear();
    // Note: We don't clear the int array as it's not necessary
  }
}
