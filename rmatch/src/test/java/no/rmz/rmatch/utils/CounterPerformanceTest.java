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

import org.junit.jupiter.api.Test;

/** Basic throughput probe for the FastCounter system. */
public class CounterPerformanceTest {

  private static final int ITERATIONS = 10_000;
  private static final int WARMUP_ITERATIONS = 1_000;

  @Test
  public void runFastCounterThroughputProbe() {
    System.out.println("FastCounter Throughput Probe");
    System.out.println("====================================");

    // Warmup
    warmupFastCounters();

    // Test FastCounters
    long fastTime = testFastCounters();
    System.out.printf("FastCounters: %,d ns (%d iterations)%n", fastTime, ITERATIONS);
    System.out.printf("Average per operation: %.2f ns%n", (double) fastTime / ITERATIONS);
  }

  private void warmupFastCounters() {
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      FastCounter counter =
          FastCounters.newCounter(CounterType.values()[i % CounterType.values().length]);
      counter.inc();
    }
  }

  private long testFastCounters() {
    CounterType[] types = CounterType.values();
    long startTime = System.nanoTime();

    for (int i = 0; i < ITERATIONS; i++) {
      // Use modulo to cycle through counter types (simulating real usage)
      CounterType type = types[i % types.length];
      FastCounter counter = FastCounters.newCounter(type);
      counter.inc();
    }

    return System.nanoTime() - startTime;
  }
}
