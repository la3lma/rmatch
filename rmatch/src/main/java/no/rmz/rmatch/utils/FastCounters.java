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

import java.util.EnumMap;
import java.util.Map;
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

  /** Get a point-in-time snapshot of all counters. */
  public static Map<CounterType, Long> snapshot() {
    return INSTANCE.privateSnapshot();
  }

  /** Dump all counters to stdout for debugging. */
  public static void dumpCounters() {
    INSTANCE.privateDumpCounters();
  }

  private FastCounter privateNewCounter(final CounterType type) {
    return new FastCounter(type, counters);
  }

  private Map<CounterType, Long> privateSnapshot() {
    final Map<CounterType, Long> result = new EnumMap<>(CounterType.class);
    for (final CounterType type : CounterType.values()) {
      result.put(type, counters.get(type.ordinal()));
    }
    return result;
  }

  private void privateDumpCounters() {
    for (final Map.Entry<CounterType, Long> entry : privateSnapshot().entrySet()) {
      final CounterType type = entry.getKey();
      final long value = entry.getValue();
      System.out.println("#'" + type.getLegacyName() + "'=" + value);
    }
  }
}
