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

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * High-performance counter system using primitive arrays indexed by operation type.
 *
 * <p>Replaces the original string-based counter system to eliminate string hashing overhead, reduce
 * synchronization complexity, and provide O(1) counter access.
 *
 * <p>Thread-safe implementation using {@link AtomicLongArray} for lock-free operations.
 */
public final class FastCounters {

  /** The singleton instance. */
  private static final FastCounters INSTANCE = new FastCounters();

  /** Array of atomic counters, indexed by CounterType ordinal. */
  private final AtomicLongArray counters;

  /** Private constructor for singleton pattern. */
  private FastCounters() {
    this.counters = new AtomicLongArray(CounterType.values().length);
  }

  /**
   * Get a fast counter for the specified counter type.
   *
   * @param type the type of counter to get
   * @return a FastCounter instance
   */
  public static FastCounter newCounter(final CounterType type) {
    return INSTANCE.privateNewCounter(type);
  }

  /**
   * Get all counters as a collection for compatibility with legacy code. Creates new Counter
   * instances for debugging/reporting purposes.
   *
   * @return collection of counter representations
   */
  public static Collection<Counter> getCounters() {
    return INSTANCE.privateGetCounters();
  }

  /** Dump all counters to stdout for debugging. */
  public static void dumpCounters() {
    INSTANCE.privateDumpCounters();
  }

  private FastCounter privateNewCounter(final CounterType type) {
    return new FastCounter(type, counters);
  }

  private Collection<Counter> privateGetCounters() {
    final Collection<Counter> result = new ArrayList<>();
    for (final CounterType type : CounterType.values()) {
      final long value = counters.get(type.ordinal());
      // Create a new Counter for compatibility - it won't be used for actual counting
      Counter reportingCounter = new Counter(type.getLegacyName(), false);
      // Use reflection to set the internal AtomicInteger value to match our fast counter
      setCounterValue(reportingCounter, value);
      result.add(reportingCounter);
    }
    return result;
  }

  private void privateDumpCounters() {
    for (final CounterType type : CounterType.values()) {
      final long value = counters.get(type.ordinal());
      System.out.println("#'" + type.getLegacyName() + "'=" + value);
    }
  }

  private void setCounterValue(final Counter counter, final long value) {
    try {
      // Use reflection to access the private atomicInt field and set its value
      java.lang.reflect.Field atomicField = Counter.class.getDeclaredField("atomicInt");
      atomicField.setAccessible(true);
      java.util.concurrent.atomic.AtomicInteger atomic =
          (java.util.concurrent.atomic.AtomicInteger) atomicField.get(counter);
      atomic.set((int) Math.min(value, Integer.MAX_VALUE));
    } catch (Exception e) {
      // If reflection fails, ignore - the counter will just show 0
      // This is only used for debugging output anyway
    }
  }
}
