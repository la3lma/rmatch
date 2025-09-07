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

/**
 * Performance comparison test between the old string-based counters and the new FastCounter system.
 */
public class CounterPerformanceTest {

  private static final int ITERATIONS = 10_000;
  private static final int WARMUP_ITERATIONS = 1_000;

  @Test
  public void compareCounterPerformance() {
    System.out.println("Counter Performance Comparison Test");
    System.out.println("====================================");

    // Warmup both systems
    warmupOldCounters();
    warmupFastCounters();

    // Test old string-based counters
    long oldTime = testOldCounters();
    System.out.printf("Old string-based counters: %,d ns (%d iterations)%n", oldTime, ITERATIONS);
    System.out.printf("Average per operation: %.2f ns%n", (double) oldTime / ITERATIONS);

    // Test new FastCounters
    long fastTime = testFastCounters();
    System.out.printf("New FastCounters: %,d ns (%d iterations)%n", fastTime, ITERATIONS);
    System.out.printf("Average per operation: %.2f ns%n", (double) fastTime / ITERATIONS);

    // Calculate improvement
    double improvement = ((double) (oldTime - fastTime) / oldTime) * 100;
    double speedup = (double) oldTime / fastTime;

    System.out.println();
    System.out.printf("Performance improvement: %.2f%%%n", improvement);
    System.out.printf("Speedup: %.2fx%n", speedup);

    if (improvement > 1.0) {
      System.out.println("✓ Performance improvement achieved (>1%)");
    } else {
      System.out.println("⚠ Performance improvement less than 1%");
    }
  }

  private void warmupOldCounters() {
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      // Create unique names for each counter
      Counter counter = Counters.newCounter("warmup-old-" + i, false);
      counter.inc();
    }
  }

  private void warmupFastCounters() {
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      FastCounter counter =
          FastCounters.newCounter(CounterType.values()[i % CounterType.values().length]);
      counter.inc();
    }
  }

  private long testOldCounters() {
    long startTime = System.nanoTime();

    for (int i = 0; i < ITERATIONS; i++) {
      // Create unique counter names since old system doesn't allow duplicates
      String counterName = "test-old-" + i;
      Counter counter = Counters.newCounter(counterName, false);
      counter.inc();
    }

    return System.nanoTime() - startTime;
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
