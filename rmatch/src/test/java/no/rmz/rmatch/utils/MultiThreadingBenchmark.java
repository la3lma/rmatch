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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Simple benchmark to measure multi-threaded performance improvements with lock-free data
 * structures.
 */
public class MultiThreadingBenchmark {

  private static final int NUM_THREADS = 4;
  private static final int OPERATIONS_PER_THREAD = 10000;

  @Test
  public void runBenchmark() throws InterruptedException {
    System.out.println("Multi-threading benchmark for lock-free data structures");
    System.out.println("Threads: " + NUM_THREADS);
    System.out.println("Operations per thread: " + OPERATIONS_PER_THREAD);
    System.out.println();

    benchmarkCounters();
    System.out.println();
    benchmarkSimulatedHeap();
  }

  private static void benchmarkCounters() throws InterruptedException {
    System.out.println("=== Counter Benchmark ===");

    long startTime = System.nanoTime();
    ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
    CountDownLatch latch = new CountDownLatch(NUM_THREADS);
    AtomicLong totalOperations = new AtomicLong(0);

    for (int i = 0; i < NUM_THREADS; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            try {
              for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                String counterName = "bench_counter_" + threadId + "_" + j;
                Counter counter = Counters.newCounter(counterName);
                counter.inc();
                counter.toString(); // Exercise the toString method
                totalOperations.incrementAndGet();
              }
            } finally {
              latch.countDown();
            }
          });
    }

    latch.await();
    long endTime = System.nanoTime();
    executor.shutdown();

    double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
    double operationsPerSecond = totalOperations.get() / durationSeconds;

    System.out.printf("Counter operations completed: %d\n", totalOperations.get());
    System.out.printf("Duration: %.3f seconds\n", durationSeconds);
    System.out.printf("Throughput: %.0f operations/second\n", operationsPerSecond);
  }

  private static void benchmarkSimulatedHeap() throws InterruptedException {
    System.out.println("=== SimulatedHeap Benchmark ===");

    SimulatedHeap<Integer> heap = new SimulatedHeap<>(Integer::compareTo);
    long startTime = System.nanoTime();
    ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
    CountDownLatch latch = new CountDownLatch(NUM_THREADS);
    AtomicLong totalOperations = new AtomicLong(0);

    for (int i = 0; i < NUM_THREADS; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            try {
              for (int j = 0; j < OPERATIONS_PER_THREAD / 10; j++) { // Fewer operations for heap
                Integer value = threadId * OPERATIONS_PER_THREAD + j;

                // Add operation
                heap.add(value);
                totalOperations.incrementAndGet();

                // Contains check
                heap.contains(value);
                totalOperations.incrementAndGet();

                // Size check
                heap.size();
                totalOperations.incrementAndGet();
              }
            } finally {
              latch.countDown();
            }
          });
    }

    latch.await();
    long endTime = System.nanoTime();
    executor.shutdown();

    double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
    double operationsPerSecond = totalOperations.get() / durationSeconds;

    System.out.printf("Heap operations completed: %d\n", totalOperations.get());
    System.out.printf("Final heap size: %d\n", heap.size());
    System.out.printf("Duration: %.3f seconds\n", durationSeconds);
    System.out.printf("Throughput: %.0f operations/second\n", operationsPerSecond);
  }
}
