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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Tests for concurrent access to lock-free data structures. */
public class ConcurrencyTest {

  @Test
  public void testCountersConcurrency() throws InterruptedException {
    final int numThreads = 10;
    final int operationsPerThread = 1000;
    final CountDownLatch latch = new CountDownLatch(numThreads);
    final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    final AtomicInteger errorCount = new AtomicInteger(0);

    for (int i = 0; i < numThreads; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            try {
              for (int j = 0; j < operationsPerThread; j++) {
                String counterName = "counter_" + threadId + "_" + j;
                Counter counter = Counters.newCounter(counterName);
                assertNotNull(counter);
                counter.inc();
                assertTrue(counter.toString().contains(counterName));
              }
            } catch (Exception e) {
              errorCount.incrementAndGet();
            } finally {
              latch.countDown();
            }
          });
    }

    assertTrue(latch.await(30, TimeUnit.SECONDS));
    assertEquals(0, errorCount.get(), "Should not have any errors in concurrent counter creation");
    executor.shutdown();
  }

  @Test
  public void testSimulatedHeapConcurrency() throws InterruptedException {
    final SimulatedHeap<Integer> heap = new SimulatedHeap<>(Integer::compareTo);
    final int numThreads = 5;
    final int operationsPerThread = 200;
    final CountDownLatch latch = new CountDownLatch(numThreads);
    final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    final AtomicInteger errorCount = new AtomicInteger(0);

    for (int i = 0; i < numThreads; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            try {
              List<Integer> addedValues = new ArrayList<>();
              // Add values
              for (int j = 0; j < operationsPerThread; j++) {
                Integer value = threadId * operationsPerThread + j;
                heap.add(value);
                addedValues.add(value);
              }

              // Check contains
              for (Integer value : addedValues) {
                assertTrue(heap.contains(value));
              }

              // Remove half of them
              for (int j = 0; j < operationsPerThread / 2; j++) {
                Integer value = addedValues.get(j);
                heap.remove(value);
                assertFalse(heap.contains(value));
              }
            } catch (Exception e) {
              errorCount.incrementAndGet();
              e.printStackTrace();
            } finally {
              latch.countDown();
            }
          });
    }

    assertTrue(latch.await(30, TimeUnit.SECONDS));
    assertEquals(0, errorCount.get(), "Should not have any errors in concurrent heap operations");

    // Verify heap is still functional
    assertTrue(heap.size() > 0);
    assertFalse(heap.isEmpty());

    executor.shutdown();
  }
}
