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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.atomic.AtomicInteger;

/** A counter that will give integer values starting from zero. */
public final class Counter {

  /** True iff counter can be decremented. */
  private final boolean canBeDecremented;

  /** Name of the counter. Lives in a global address space so all counter names has to be unique. */
  private final String name;

  private final AtomicInteger atomicInt = new AtomicInteger(0);

  /**
   * Create a new counter. The name needs to be unique. By default counters can be both incremented
   * and decremented.
   *
   * @param name The name of the counter.
   * @param canBeDecremented
   */
  public Counter(final String name, final boolean canBeDecremented) {
    this.name = checkNotNull(name, "Counter name can't be null").trim();
    this.canBeDecremented = canBeDecremented;
    checkArgument(!name.isEmpty(), "Counter name can't be empty string");
  }

  public Counter(final String name) {
    this(name, false);
  }

  /**
   * Increment the counter by one and return the new value.
   *
   * @return the new value.
   */
  public long inc() {
    return atomicInt.addAndGet(1);
  }

  /**
   * Decrement the counter by one and return the new value.
   *
   * @return the new value.
   */
  public long dec() {

    if (!canBeDecremented) {
      throw new IllegalStateException("Can't decrement a counter that can't be decremented");
    }
    return (atomicInt.addAndGet(-1));
  }

  @Override
  public String toString() {
    return "#'" + name + "'=" + atomicInt;
  }
}
