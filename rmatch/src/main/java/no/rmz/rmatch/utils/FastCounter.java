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

import static no.rmz.rmatch.internal.Checks.checkNotNull;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Lightweight counter backed by a shared atomic array.
 *
 * <p>This class is used by rmatch diagnostics to count hot-path events without paying for string
 * keys or map lookups on every increment.
 */
public final class FastCounter {
  private final CounterType type;
  private final AtomicLongArray countersArray;
  private final int index;

  /**
   * Create a counter view over one slot in the shared counter array.
   *
   * @param type the counter type
   * @param countersArray the shared atomic array for all counters
   */
  FastCounter(final CounterType type, final AtomicLongArray countersArray) {
    this.type = checkNotNull(type, "Counter type cannot be null");
    this.countersArray = checkNotNull(countersArray, "Counters array cannot be null");
    this.index = type.ordinal();
  }

  /**
   * Increment this counter by one.
   *
   * @return the new value
   */
  public long inc() {
    return countersArray.incrementAndGet(index);
  }

  /**
   * Decrement this counter by one.
   *
   * @return the new value
   */
  public long dec() {
    return countersArray.decrementAndGet(index);
  }

  /**
   * Return the current counter value.
   *
   * @return the current value
   */
  public long get() {
    return countersArray.get(index);
  }

  @Override
  public String toString() {
    return "#'" + type.getLegacyName() + "'=" + get();
  }
}
